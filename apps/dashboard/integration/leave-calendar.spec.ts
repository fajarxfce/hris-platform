import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "./fixtures/admin-company";
import { createLeaveScenario } from "./fixtures/leave-scenario";

test("real calendar counts charged dates and preserves selection through withdrawal before refreshing occupancy", async ({
  page,
  context,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-leave-calendar-admin@example.invalid",
    "LEAVECALENDAR",
    "Leave Calendar Browser",
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
          permissions: ["leave.approve"],
          reason: "Calendar fixture independent review",
        },
      })
    ).status(),
  ).toBe(200);
  const scenario = await createLeaveScenario(context, company, reviewer);
  const second = randomUUID();
  expect(
    (
      await context.request.post(`${scenario.base}/leave/requests`, {
        headers: headers(),
        data: {
          id: second,
          employeeId: scenario.employee,
          typeId: scenario.type,
          days: [{ workDate: scenario.date, portion: "SECOND_HALF" }],
          reason: "Independent second-half fixture",
        },
      })
    ).status(),
  ).toBe(200);
  const otherResponse = await context.request.post("/api/v1/companies", {
    headers: headers(),
    data: { code: "LEAVECALENDAROTHER", name: "Other Calendar Company", timezone: "Asia/Jakarta" },
  });
  expect(otherResponse.status()).toBe(200);
  const other: string = (await otherResponse.json()).id;
  const month = scenario.date.slice(0, 7);
  const response = await context.request.get(
    `${scenario.base}/leave/calendar?scope=COMPANY&month=${month}`,
  );
  expect(response.status()).toBe(200);
  const summary = await response.json();
  expect(summary).toMatchObject({
    month,
    scope: "COMPANY",
    timezone: "Asia/Jakarta",
    days: [
      {
        date: scenario.date,
        employeeCount: 1,
        requestCount: 2,
        pendingCount: 2,
        approvedCount: 0,
        cancellationPendingCount: 0,
      },
    ],
  });
  const dateLabel = new Intl.DateTimeFormat("en", { dateStyle: "full", timeZone: "UTC" }).format(
    new Date(`${scenario.date}T00:00:00Z`),
  );
  await page.goto(
    `/leave/calendar?company=${company}&scope=COMPANY&month=${month}&date=${scenario.date}`,
  );
  const entries = page.getByRole("table", { name: "Leave on selected date", exact: true });
  await expect(entries.getByRole("row")).toHaveCount(3);
  await expect(entries).toContainText("First half");
  await expect(entries).toContainText("Second half");
  await expect(page.getByRole("region", { name: dateLabel, exact: true })).toContainText(
    "1 employee · 2 requests",
  );
  await entries
    .getByRole("button", { name: `View request: ${scenario.employeeName}`, exact: true })
    .first()
    .click();
  const opened = new URL(page.url()).pathname.split("/").at(-1);
  expect([scenario.request, second]).toContain(opened);
  await page.getByRole("link", { name: "Withdraw request", exact: true }).click();
  await page
    .getByLabel("Action reason", { exact: true })
    .fill("Calendar request withdrawn after review");
  await page.getByRole("button", { name: "Confirm action", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Action recorded.");
  await page.getByRole("link", { name: "View current request", exact: true }).click();
  await page.getByRole("link", { name: "Back to calendar", exact: true }).click();
  await expect(entries.getByRole("row")).toHaveCount(2);
  await expect(page.getByRole("region", { name: dateLabel, exact: true })).toContainText(
    "1 employee · 1 request",
  );
  expect(new URL(page.url()).searchParams.get("date")).toBe(scenario.date);
  const remaining = opened === scenario.request ? second : scenario.request;
  expect(
    (
      await context.request.post(`${scenario.base}/leave/requests/${remaining}/withdraw`, {
        headers: headers(),
        data: { version: 0, reason: "Remaining calendar request withdrawn" },
      })
    ).status(),
  ).toBe(200);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No leave on this date.");
  await expect(page.getByRole("region", { name: dateLabel, exact: true })).toContainText(
    "0 employees · 0 requests",
  );
  const calendar = await (
    await context.request.get(`${scenario.base}/leave/calendar?scope=COMPANY&month=${month}`)
  ).json();
  expect(calendar.days).toEqual([]);
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(other);
  await expect(page.getByRole("status")).toContainText("No leave on this date.");
  expect(new URL(page.url()).searchParams.get("company")).toBe(other);
  expect(new URL(page.url()).searchParams.has("date")).toBe(false);
  await expect(page.getByRole("main")).not.toContainText(scenario.employeeName);
});
