import { randomUUID } from "node:crypto";
import { expect, type Page, test } from "@playwright/test";
import type { OvertimeDetailsDto } from "../src/features/workforce/data/models/overtime-details-dto";
import type { OvertimePlanDto } from "../src/features/workforce/data/models/overtime-plan-dto";
import { createAdminCompany } from "./fixtures/admin-company";
import { createOvertimeScenario } from "./fixtures/overtime-scenario";
import { signInAndEnrollAuthenticator } from "./fixtures/sign-in-and-enroll-authenticator";

async function loseFirstReceipt(page: Page, resource: string, id: string, version: number) {
  const commands: { operation: string; body: unknown }[] = [];
  const pattern = `**${resource}`;
  await page.route(pattern, async (route) => {
    const operation = route.request().headers()["idempotency-key"] ?? "";
    expect(operation).toMatch(/^[a-f0-9-]{36}$/u);
    commands.push({ operation, body: route.request().postDataJSON() });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual({ id, version });
    if (commands.length === 1) return route.abort("failed");
    return route.fulfill({ response });
  });
  return { commands, stop: () => page.unroute(pattern) };
}

test("overtime planning and actions recover real receipts, preserve original evidence and enforce independent access", async ({
  page,
  context,
  browser,
  request,
}) => {
  test.setTimeout(150_000);
  const { company } = await createAdminCompany(
    page,
    context,
    "browser-overtime-admin@example.invalid",
    "BROWSER_OVERTIME",
    "Browser Overtime",
  );
  const preparation = await createOvertimeScenario(context, company);
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  const query = `company=${company}&from=${preparation.workDate}&until=${preparation.workDate}`;
  await page.goto(`/workforce/overtime?${query}`);
  await page.getByRole("button", { name: "New overtime request", exact: true }).click();
  await page.getByLabel("Work date", { exact: true }).fill(preparation.workDate);
  await page.getByRole("button", { name: "Select employee", exact: true }).click();
  const picker = page.getByRole("dialog", { name: "Select employee", exact: true });
  await picker
    .getByRole("button", { name: "Select: Overtime Fixture Employee · OVERTIME-001", exact: true })
    .click();
  await expect(page.getByRole("region", { name: "Work schedule", exact: true })).toContainText(
    "Day off",
  );
  await page.getByLabel("Start", { exact: true }).fill(`${preparation.workDate}T08:00`);
  await page.getByLabel("End", { exact: true }).fill(`${preparation.workDate}T11:00`);
  await page.getByLabel("Reason", { exact: true }).fill("Inventory count on the original day off");
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-overtime-plan-real-api.png",
    fullPage: true,
    animations: "disabled",
  });
  const planningCommands: { operation: string; body: OvertimePlanDto }[] = [];
  const planPattern = `**${preparation.planPath}`;
  await page.route(planPattern, async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    const operation = route.request().headers()["idempotency-key"] ?? "",
      body = route.request().postDataJSON() as OvertimePlanDto;
    planningCommands.push({ operation, body });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual({ id: body.id, version: 0 });
    if (planningCommands.length === 1) {
      // The committed request retains its original calendar, including on lost-response recovery.
      const changed = await context.request.put(
        `${preparation.base}/workforce/holidays/${preparation.holiday}`,
        {
          headers: preparation.headers(),
          data: {
            workDate: preparation.workDate,
            name: "Inventory holiday",
            expectedVersion: 0,
            active: false,
            reason: "Later calendar change",
          },
        },
      );
      expect(changed.status()).toBe(200);
      return route.abort("failed");
    }
    return route.fulfill({ response });
  });
  await page.getByRole("button", { name: "Create request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result could not be confirmed");
  await page.getByRole("button", { name: "Retry this request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Overtime request created.");
  await page.unroute(planPattern);
  expect(planningCommands).toHaveLength(2);
  expect(planningCommands[1]).toEqual(planningCommands[0]);
  const originalPlan = planningCommands[0];
  if (!originalPlan) throw new Error("Missing planned request");
  expect(originalPlan.body.planningRevision).toMatch(/^[0-9a-f]{64}$/u);
  const scenario = {
    ...preparation,
    id: originalPlan.body.id,
    plan: originalPlan.body,
    planKey: originalPlan.operation,
    plannedReceipt: { id: originalPlan.body.id, version: 0 },
    overtimePath: `${preparation.planPath}/${originalPlan.body.id}`,
  };
  await page.getByRole("link", { name: "View request", exact: true }).click();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "Planned",
  );
  await page.getByRole("link", { name: "Submit actual time", exact: true }).click();
  await page.getByLabel("Actual start", { exact: true }).fill(`${scenario.workDate}T08:15`);
  await page.getByLabel("Actual end", { exact: true }).fill(`${scenario.workDate}T10:45`);
  await page.getByLabel("Break (min)", { exact: true }).fill("15");
  await page.getByLabel("Reason", { exact: true }).fill("Inventory completed");
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-overtime-actual-real-api.png",
    fullPage: true,
    animations: "disabled",
  });
  const actualDelivery = await loseFirstReceipt(
    page,
    `${scenario.overtimePath}/actual`,
    scenario.id,
    1,
  );
  await page.getByRole("button", { name: "Submit actual time", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result could not be confirmed");
  await page.getByRole("button", { name: "Retry original action", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Actual time submitted for review.");
  expect(actualDelivery.commands).toHaveLength(2);
  expect(actualDelivery.commands[1]).toEqual(actualDelivery.commands[0]);
  expect(actualDelivery.commands[0]?.body).toEqual(scenario.actual);
  const actualKey = actualDelivery.commands[0]?.operation;
  if (!actualKey) throw new Error("Missing actual operation");
  await actualDelivery.stop();
  await page.getByRole("link", { name: "View current request", exact: true }).click();
  const overview = page.getByRole("region", { name: "Overview", exact: true });
  await expect(overview).toContainText("Overtime Fixture Employee");
  await expect(page.getByRole("region", { name: "Original schedule", exact: true })).toContainText(
    "Day off",
  );
  const records = page.getByRole("table", { name: "Time records", exact: true });
  await expect(records.getByRole("row").filter({ hasText: "Requested" })).toContainText("180");
  await expect(records.getByRole("row").filter({ hasText: "Actual" })).toContainText("135");
  await page.getByRole("button", { name: "View change: 0", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Change details", exact: true });
  await expect(panel).toContainText("Inventory count on the original day off");
  await panel.getByRole("button", { name: "Close", exact: true }).click();
  const selfApproval = await context.request.post(`${scenario.overtimePath}/decisions`, {
    headers: scenario.headers(),
    data: { expectedVersion: 1, decision: "APPROVE", reason: "Maker cannot approve" },
  });
  expect(selfApproval.status()).toBe(403);
  expect(await selfApproval.json()).toMatchObject({ code: "self_approval_denied" });
  await expect(page.getByRole("link", { name: "Approve overtime", exact: true })).toHaveCount(0);

  const reportQuery = `company=${company}&from=${scenario.workDate.slice(0, 4)}-01-01&until=${scenario.workDate}`;
  await page.goto(`/reports/overtime?${reportQuery}`);
  await expect(page.getByRole("region", { name: "Requests", exact: true })).toContainText("1");
  await expect(page.getByRole("region", { name: "Requested minutes", exact: true })).toContainText(
    "180",
  );
  await expect(page.getByRole("region", { name: "Actual minutes", exact: true })).toContainText(
    "135",
  );
  await expect(page.getByRole("region", { name: "Approved minutes", exact: true })).toContainText(
    "0",
  );
  await page.getByLabel("Status", { exact: true }).selectOption("PENDING");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page).toHaveURL(/status=PENDING/u);
  const monthAction = page
    .getByRole("table", { name: "By work month", exact: true })
    .getByRole("button", { name: /^View requests:/u })
    .last();
  await expect(monthAction).toBeEnabled();
  await monthAction.click();
  await expect(page).toHaveURL(
    new RegExp(
      `from=${scenario.workDate.slice(0, 7)}-01&until=${scenario.workDate}&status=PENDING`,
      "u",
    ),
  );
  const requestTable = page.getByRole("table", { name: "Overtime requests", exact: true });
  await expect(requestTable.getByRole("row")).toHaveCount(2);
  await page.goBack();
  await expect(page.getByLabel("Status", { exact: true })).toHaveValue("PENDING");
  await expect(page.getByRole("region", { name: "Actual minutes", exact: true })).toContainText(
    "135",
  );

  const reviewer = await browser.newContext({ baseURL: new URL(page.url()).origin });
  try {
    const review = await reviewer.newPage();
    await signInAndEnrollAuthenticator(review, "browser-overtime-reviewer@example.invalid");
    await review.goto(`/approvals?company=${company}`);
    await review
      .getByRole("table", { name: "Approval inbox", exact: true })
      .getByRole("button")
      .click();
    await review.getByRole("link", { name: "View source request", exact: true }).click();
    await expect(review.getByRole("region", { name: "Overview", exact: true })).toContainText(
      "Overtime Fixture Employee",
    );
    await expect(
      review.getByRole("link", { name: "Back to approvals", exact: true }),
    ).toBeVisible();
    await expect(
      review.getByRole("navigation").getByRole("link", { name: "Overtime", exact: true }),
    ).toHaveCount(0);
    expect(
      (
        await reviewer.request.get(
          `${scenario.planPath}?from=${scenario.workDate}&until=${scenario.workDate}`,
        )
      ).status(),
    ).toBe(403);
    await review.getByRole("link", { name: "Approve overtime", exact: true }).click();
    await review.getByLabel("Reason", { exact: true }).fill("Independently verified");
    const decisionDelivery = await loseFirstReceipt(
      review,
      `${scenario.overtimePath}/decisions`,
      scenario.id,
      2,
    );
    await review.getByRole("button", { name: "Approve overtime", exact: true }).click();
    await expect(review.getByRole("status")).toContainText("The result could not be confirmed");
    await review.getByRole("button", { name: "Retry original action", exact: true }).click();
    await expect(review.getByRole("status")).toContainText("Approval recorded.");
    expect(decisionDelivery.commands).toHaveLength(2);
    expect(decisionDelivery.commands[1]).toEqual(decisionDelivery.commands[0]);
    await decisionDelivery.stop();
    await review.getByRole("link", { name: "View current request", exact: true }).click();
    await expect(review.getByRole("region", { name: "Overview", exact: true })).toContainText(
      "Approved",
    );
    await expect(
      review.getByRole("table", { name: "History", exact: true }).getByRole("row"),
    ).toHaveCount(4);
    await review.screenshot({
      path: "../../.work/dashboard-overtime-real-api.png",
      fullPage: true,
      animations: "disabled",
    });

    const latestReview = await context.request.get(
      `${scenario.planPath}/planning/employees/${scenario.employee}?workDate=${scenario.workDate}`,
    );
    expect(latestReview.status()).toBe(200);
    const currentPlanningRevision = ((await latestReview.json()) as { revision: string }).revision;
    expect(currentPlanningRevision).not.toBe(scenario.plan.planningRevision);
    for (const outcome of ["planned-withdrawal", "pending-withdrawal", "rejection"] as const) {
      const id = randomUUID(),
        resource = `${scenario.planPath}/${id}`;
      const requested = {
        startsAt: `${scenario.workDate}T11:00:00Z`,
        endsAt: `${scenario.workDate}T13:00:00Z`,
        breakMinutes: 0,
      };
      const planned = await context.request.post(scenario.planPath, {
        headers: scenario.headers(),
        data: {
          ...scenario.plan,
          id,
          requested,
          planningRevision: currentPlanningRevision,
          reason: "Evening stock review",
        },
      });
      expect(planned.status()).toBe(200);
      await page.goto(`/workforce/overtime/${id}?${query}`);
      if (outcome !== "planned-withdrawal") {
        await page.getByRole("link", { name: "Submit actual time", exact: true }).click();
        await page.getByLabel("Reason", { exact: true }).fill("Evening review completed");
        await page.getByRole("button", { name: "Submit actual time", exact: true }).click();
        await expect(page.getByRole("status")).toContainText("Actual time submitted for review.");
        await page.getByRole("link", { name: "View current request", exact: true }).click();
      }
      if (outcome === "rejection") {
        await review.goto(`/workforce/overtime/${id}?${query}`);
        await review.getByRole("link", { name: "Reject overtime", exact: true }).click();
        await review
          .getByLabel("Reason", { exact: true })
          .fill("Reported work could not be verified");
        await review.getByRole("button", { name: "Reject overtime", exact: true }).click();
        await expect(review.getByRole("status")).toContainText("Request rejected.");
      } else {
        await page.getByRole("link", { name: "Withdraw overtime", exact: true }).click();
        await page.getByLabel("Reason", { exact: true }).fill("Work no longer required");
        const delivery =
          outcome === "pending-withdrawal"
            ? await loseFirstReceipt(page, `${resource}/withdraw`, id, 2)
            : null;
        await page.getByRole("button", { name: "Withdraw overtime", exact: true }).click();
        if (delivery) {
          await expect(page.getByRole("status")).toContainText("The result could not be confirmed");
          await page.getByRole("button", { name: "Retry original action", exact: true }).click();
        }
        await expect(page.getByRole("status")).toContainText("Request withdrawn.");
        if (delivery) {
          expect(delivery.commands).toHaveLength(2);
          expect(delivery.commands[1]).toEqual(delivery.commands[0]);
          await delivery.stop();
        }
      }
      const changed = (await (await context.request.get(resource)).json()) as OvertimeDetailsDto;
      expect(changed.request).toMatchObject({
        status: outcome === "rejection" ? "REJECTED" : "WITHDRAWN",
        approvedMinutes: 0,
        version: outcome === "planned-withdrawal" ? 1 : 2,
      });
      expect(changed.approval?.status ?? null).toBe(
        outcome === "rejection"
          ? "REJECTED"
          : outcome === "pending-withdrawal"
            ? "CANCELLED"
            : null,
      );
      expect(changed.history.items).toHaveLength(outcome === "planned-withdrawal" ? 2 : 3);
    }
  } finally {
    await reviewer.close();
  }

  const current = await context.request.get(scenario.overtimePath);
  expect(current.status()).toBe(200);
  const confirmed = (await current.json()) as OvertimeDetailsDto;
  expect(confirmed.request).toMatchObject({
    status: "APPROVED",
    approvedMinutes: 135,
    version: 2,
    requested: { workedMinutes: 180 },
    actual: { workedMinutes: 135 },
    schedule: { kind: "OFF", holidayId: scenario.holiday },
  });
  expect(confirmed.history.items).toHaveLength(3);
  for (const [path, key, data, expected] of [
    [scenario.planPath, scenario.planKey, scenario.plan, scenario.plannedReceipt],
    [
      `${scenario.overtimePath}/actual`,
      actualKey,
      scenario.actual,
      { id: scenario.id, version: 1 },
    ],
  ] as const) {
    const replay = await context.request.post(path, {
      headers: { ...scenario.headers(), "Idempotency-Key": key },
      data,
    });
    expect(replay.status()).toBe(200);
    expect(await replay.json()).toEqual(expected);
  }
  const native = await request.post("/api/v1/auth/native/login", {
    headers: { "Idempotency-Key": randomUUID() },
    data: {
      email: "browser-overtime-employee@example.invalid",
      password: "Browser-fixture-password-123!",
      deviceName: "Overtime browser acceptance",
    },
  });
  expect(native.status()).toBe(200);
  const { credentials } = (await native.json()) as { credentials: { accessToken: string } };
  const nativeHeaders = {
    Authorization: `Bearer ${credentials.accessToken}`,
    "X-HRIS-Client-Platform": "ANDROID",
    "X-HRIS-Client-Build": "1",
  };
  const owned = await request.get(scenario.overtimePath, { headers: nativeHeaders });
  expect(owned.status()).toBe(200);
  expect(((await owned.json()) as OvertimeDetailsDto).request.approvedMinutes).toBe(135);
  const other = await context.request.post("/api/v1/companies", {
    headers: scenario.headers(),
    data: {
      code: "BROWSER_OVERTIME_OTHER",
      name: "Other Overtime Company",
      timezone: "Asia/Jakarta",
    },
  });
  expect(other.status()).toBe(200);
  const otherId = ((await other.json()) as { id: string }).id;
  expect(
    (
      await context.request.get(`/api/v1/companies/${otherId}/workforce/overtime/${scenario.id}`)
    ).status(),
  ).toBe(404);
  await page.goto(`/workforce/overtime/${scenario.id}?${query}`);
  await expect(overview).toContainText("Approved");
  await expect(
    page.getByRole("table", { name: "History", exact: true }).getByRole("row"),
  ).toHaveCount(4);
  await page.goto(`/reports/overtime?${reportQuery}`);
  for (const [name, total] of [
    ["Requests", "4"],
    ["Employment records", "1"],
    ["Requests with actual times", "3"],
    ["Requested minutes", "540"],
    ["Actual minutes", "375"],
    ["Approved minutes", "135"],
  ] as const)
    await expect(page.getByRole("region", { name, exact: true })).toContainText(total);
  await page.screenshot({
    path: "../../.work/dashboard-overtime-report-real.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByLabel("Status", { exact: true }).selectOption("WITHDRAWN");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  for (const [name, total] of [
    ["Requests", "2"],
    ["Requests with actual times", "1"],
    ["Requested minutes", "240"],
    ["Actual minutes", "120"],
    ["Approved minutes", "0"],
  ] as const)
    await expect(page.getByRole("region", { name, exact: true })).toContainText(total);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("region", { name: "Requests", exact: true })).toContainText("2");
});
