import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import type { LeaveRequestSubmissionDto } from "../src/features/leave/data/models/leave-request-submission-dto";
import { createAdminCompany } from "./fixtures/admin-company";
import { createLeaveScenario } from "./fixtures/leave-scenario";

test("real API creation recalculates changed balances and replays a lost submission exactly once", async ({
  page,
  context,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-leave-creation-admin@example.invalid",
    "LEAVECREATION",
    "Leave Creation Browser",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const reviewer = "a0000000-0000-4000-8000-000000000037";
  expect(
    (
      await context.request.put(`/api/v1/companies/${company}/members/${reviewer}`, {
        headers: headers(),
        data: {
          active: true,
          permissions: ["leave.approve", "leave.read"],
          reason: "Independent fixture review",
        },
      })
    ).status(),
  ).toBe(200);
  const scenario = await createLeaveScenario(context, company, reviewer, { submit: false });
  const otherResponse = await context.request.post("/api/v1/companies", {
    headers: headers(),
    data: { code: "LEAVECREATIONOTHER", name: "Leave Creation Other", timezone: "Asia/Jakarta" },
  });
  expect(otherResponse.status()).toBe(200);
  const other = ((await otherResponse.json()) as { id: string }).id;
  const followingDay = new Date(`${scenario.date}T00:00:00Z`);
  followingDay.setUTCDate(followingDay.getUTCDate() + 1);
  const until = followingDay.toISOString().slice(0, 10);
  const balancePath = `${scenario.base}/leave/employees/${scenario.employee}/balances/${scenario.type}/${scenario.date.slice(0, 4)}`;
  await page.goto(`/leave/requests?company=${company}`);
  await page.getByRole("link", { name: "New request", exact: true }).click();
  await page.getByRole("button", { name: "Choose employee", exact: true }).click();
  await page.getByRole("button", { name: `Select: ${scenario.employeeName}`, exact: true }).click();
  await page.getByLabel("Start date", { exact: true }).fill(scenario.date);
  await page.getByLabel("End date", { exact: true }).fill(until);
  await page.getByRole("button", { name: "Choose leave type", exact: true }).click();
  await page.getByRole("button", { name: "Select: Browser annual leave", exact: true }).click();
  await page.getByRole("combobox", { name: "First day", exact: true }).selectOption("FIRST_HALF");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed family appointment");
  await page.getByRole("button", { name: "Review request", exact: true }).click();
  const review = page.getByRole("complementary", { name: "Request review", exact: true });
  await expect(
    review.getByRole("table", { name: "Balance after submission", exact: true }),
  ).toContainText("1.5");
  await expect(review.getByRole("table", { name: "Days included", exact: true })).toContainText(
    "First half",
  );
  await expect(review).toContainText("Dates excluded by the work schedule");
  const openingBalance = (await (await context.request.get(balancePath)).json()).balance;
  expect(openingBalance).toMatchObject({ availableDays: "2", reservedDays: "0" });

  // An independent balance change invalidates the reviewed calculation before any reservation.
  const adjusted = await context.request.post(`${balancePath}/adjustments`, {
    headers: headers(),
    data: { days: "1", expectedVersion: openingBalance.version, reason: "New fixture entitlement" },
  });
  expect(adjusted.status()).toBe(200);
  const commands: { operation: string | undefined; body: LeaveRequestSubmissionDto }[] = [];
  const statuses: number[] = [];
  await page.route(`**${scenario.base}/leave/requests`, async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    commands.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    statuses.push(response.status());
    if (response.status() === 200 && commands.length === 2) return route.abort("failed");
    return route.fulfill({ response });
  });
  await page.getByRole("button", { name: "Submit request", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Review");
  expect(statuses).toEqual([409]);
  const beforeSubmission = (await (await context.request.get(balancePath)).json()).balance;
  expect(beforeSubmission).toMatchObject({ availableDays: "3", reservedDays: "0" });
  const empty = await (await context.request.get(`${scenario.base}/leave/requests`)).json();
  expect(empty.items).toEqual([]);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue(
    "Reviewed family appointment",
  );

  await page.getByRole("button", { name: "Review request", exact: true }).click();
  await expect(
    review.getByRole("table", { name: "Balance after submission", exact: true }),
  ).toContainText("2.5");
  await page.getByRole("button", { name: "Submit request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("submission result is unconfirmed");
  await page.getByRole("button", { name: "Retry submission", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Leave request submitted.");
  expect(statuses).toEqual([409, 200, 200]);
  expect(commands).toHaveLength(3);
  expect(commands[1]).toEqual(commands[2]);
  expect(commands[0]?.operation).not.toEqual(commands[1]?.operation);
  expect(commands[0]?.body.previewRevision).not.toEqual(commands[1]?.body.previewRevision);
  const saved = commands[1];
  expect(saved).toBeDefined();
  if (!saved) throw new Error("Missing submission");
  expect(saved.body.previewRevision).toMatch(/^[a-f0-9]{64}$/u);
  expect(saved.body.days).toEqual([
    { workDate: scenario.date, portion: "FIRST_HALF" },
    { workDate: until, portion: "FULL" },
  ]);
  const requests = await (await context.request.get(`${scenario.base}/leave/requests`)).json();
  expect(requests.items).toHaveLength(1);
  expect(requests.items[0]).toMatchObject({
    id: saved.body.id,
    employeeId: scenario.employee,
    status: "PENDING",
    chargedDays: "0.5",
    version: 0,
  });
  expect((await (await context.request.get(balancePath)).json()).balance).toMatchObject({
    availableDays: "2.5",
    reservedDays: "0.5",
  });
  const resource = `${scenario.base}/leave/requests/${saved.body.id}`;
  const details = await (await context.request.get(resource)).json();
  expect(details.history.items).toHaveLength(1);
  expect(details).toMatchObject({
    policy: { typeId: scenario.type, revision: 0 },
    days: [
      { workDate: scenario.date, portion: "FIRST_HALF", plannedMinutes: 450, chargedMinutes: 225 },
    ],
  });
  expect(
    (
      await context.request.get(`/api/v1/companies/${other}/leave/requests/${saved.body.id}`)
    ).status(),
  ).toBe(404);
  expect(
    (
      await context.request.post(`/api/v1/companies/${other}/leave/requests`, {
        headers: { ...headers(), "Idempotency-Key": saved.operation ?? "" },
        data: saved.body,
      })
    ).status(),
  ).toBe(404);
  await page.getByRole("link", { name: "View request", exact: true }).click();
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toContainText(
    scenario.employeeName,
  );
  await expect(page.getByRole("table", { name: "Request history", exact: true })).toContainText(
    "Reviewed family appointment",
  );
  await page.screenshot({
    path: "../../.work/dashboard-leave-creation-api.png",
    fullPage: true,
    animations: "disabled",
  });
});
