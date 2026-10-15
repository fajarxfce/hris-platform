import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import type {
  OffboardingScheduleDto,
  OffboardingScheduleReviewDto,
} from "../src/features/lifecycle/data/models/offboarding-schedule-dto";

test("scheduled departure recovers lost admission and cancellation receipts through the real worker", async ({
  page,
  context,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-offboarding-admin@example.invalid",
    "OFFBOARDING",
    "Scheduled Departure Company",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  const base = `/api/v1/companies/${company}`,
    headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const dateResponse = await context.request.get(
    `${base}/workforce/periods/${new Date().toISOString().slice(0, 7)}`,
  );
  expect(dateResponse.status()).toBe(200);
  const companyDate = ((await dateResponse.json()) as { companyDate: string }).companyDate;
  const lastDay = new Date(Date.parse(`${companyDate}T00:00:00Z`) + 86_400_000)
    .toISOString()
    .slice(0, 10);
  const employee = randomUUID(),
    template = randomUUID(),
    caseId = randomUUID(),
    account = "a0000000-0000-4000-8000-000000000056";
  expect(
    (
      await context.request.put(`${base}/members/${account}`, {
        headers: headers(),
        data: {
          active: true,
          permissions: ["people.self.read"],
          reason: "Scheduled departure fixture",
        },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await context.request.post(`${base}/employees`, {
        headers: headers(),
        data: {
          id: employee,
          employeeNumber: "DEPARTURE-001",
          person: {
            id: randomUUID(),
            accountId: account,
            legalName: "Scheduled Departure Employee",
            nationality: "ID",
          },
          terms: {
            effectiveFrom: "2025-01-01",
            startDate: "2025-01-01",
            contract: "PERMANENT",
            status: "ACTIVE",
          },
          reason: "Employment fixture",
        },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await context.request.put(`${base}/lifecycle/templates/${template}`, {
        headers: headers(),
        data: {
          code: "DEPARTURE",
          name: "Departure review",
          kind: "OFFBOARDING",
          active: true,
          tasks: [{ key: "equipment", title: "Return equipment", required: true, dueDays: 0 }],
          reason: "Departure checklist",
        },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await context.request.post(`${base}/lifecycle/cases`, {
        headers: headers(),
        data: {
          id: caseId,
          employmentId: employee,
          templateId: template,
          templateVersion: 0,
          targetDate: lastDay,
          reason: "Reviewed planned departure",
        },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await context.request.put(`${base}/lifecycle/cases/${caseId}/tasks/equipment`, {
        headers: headers(),
        data: { expectedVersion: 0, status: "DONE", reason: "Equipment returned" },
      })
    ).status(),
  ).toBe(200);
  const scheduleBase = `${base}/lifecycle/cases/${caseId}/offboarding-schedules`;
  const reviewResponse = await context.request.get(`${scheduleBase}/review`);
  expect(reviewResponse.status()).toBe(200);
  const review = (await reviewResponse.json()) as OffboardingScheduleReviewDto;
  expect(review.blockers).toEqual([]);
  const writes: { action: string; operation: string; body: unknown }[] = [];
  let loseAdmission = true,
    loseCancellation = true;
  await page.route(`**${scheduleBase}`, async (route) => {
    if (route.request().method() !== "POST") return route.continue();
    writes.push({
      action: "schedule",
      operation: route.request().headers()["idempotency-key"] ?? "",
      body: route.request().postDataJSON() as unknown,
    });
    if (!loseAdmission) return route.continue();
    loseAdmission = false;
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    await response.dispose();
    return route.abort("connectionfailed");
  });
  const filters = `company=${company}&status=OPEN`,
    path = `/people/lifecycle/cases/${caseId}/offboarding-schedules`;
  await page.goto(`${path}/new?${filters}`);
  await expect(page.getByRole("region", { name: "Review schedule", exact: true })).toContainText(
    "Scheduled Departure Employee",
  );
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed planned departure");
  await page.getByRole("button", { name: "Schedule offboarding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Offboarding scheduled.");
  const directory = await context.request.get(scheduleBase);
  expect(directory.status()).toBe(200);
  const items = ((await directory.json()) as { items: OffboardingScheduleDto[] }).items;
  expect(items).toHaveLength(1);
  const schedule = items[0];
  if (!schedule) throw new Error("Expected schedule");
  expect(schedule).toMatchObject({
    version: 0,
    status: "SCHEDULED",
    lastWorkingDate: lastDay,
    job: { status: "QUEUED", cancellationRequested: false },
  });
  expect(schedule.scheduledFor).toBe(review.scheduledFor);
  expect(writes[1]).toEqual(writes[0]);
  expect(writes[0]?.body).toMatchObject({ basisRevision: review.basisRevision });
  // A manually closed case cannot race an authorized, uncancelled schedule.
  const blocked = await context.request.post(`${base}/lifecycle/cases/${caseId}/cancel`, {
    headers: headers(),
    data: { expectedVersion: 1, reason: "Review cancellation ordering" },
  });
  expect(blocked.status()).toBe(409);
  expect(((await blocked.json()) as { code: string }).code).toBe(
    "offboarding_cancellation_required",
  );
  await page.getByRole("link", { name: "View schedule", exact: true }).click();
  await expect(page.getByRole("region", { name: "Schedule details", exact: true })).toContainText(
    "Scheduled",
  );
  await page.screenshot({
    path: "/tmp/hris-scheduled-offboarding/schedule-real-api.png",
    fullPage: true,
  });
  await page.route(`**${scheduleBase}/${schedule.id}/cancel`, async (route) => {
    writes.push({
      action: "cancel",
      operation: route.request().headers()["idempotency-key"] ?? "",
      body: route.request().postDataJSON() as unknown,
    });
    if (!loseCancellation) return route.continue();
    loseCancellation = false;
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual({ id: schedule.job.id, version: 1 });
    await response.dispose();
    return route.abort("connectionfailed");
  });
  await page.getByRole("link", { name: "Cancel schedule", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Departure postponed");
  await page.getByRole("button", { name: "Cancel schedule", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  // The future job wakes for cleanup before its cancelled receipt is retried.
  await expect
    .poll(
      async () => {
        const result = await context.request.get(`${scheduleBase}/${schedule.id}`);
        expect(result.status()).toBe(200);
        return ((await result.json()) as OffboardingScheduleDto).status;
      },
      { timeout: 30_000 },
    )
    .toBe("CANCELLED");
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Cancellation requested.");
  expect(writes[3]).toEqual(writes[2]);
  expect(writes).toHaveLength(4);
  await page.getByRole("link", { name: "View schedule", exact: true }).click();
  await expect(page.getByRole("region", { name: "Schedule details", exact: true })).toContainText(
    "Cancelled",
  );
  await expect(page.getByRole("link", { name: "Cancel schedule", exact: true })).toHaveCount(0);
  await expect(page.getByRole("alert")).toHaveCount(0);
  const employment = await context.request.get(`${base}/employees/${employee}?asOf=${companyDate}`);
  expect(employment.status()).toBe(200);
  expect(await employment.json()).toMatchObject({ terms: { status: "ACTIVE" } });
  expect(
    await (await context.request.get(`${base}/lifecycle/cases/${caseId}`)).json(),
  ).toMatchObject({ status: "OPEN", version: 1 });
  const retained = await context.request.get(scheduleBase);
  expect(((await retained.json()) as { items: unknown[] }).items).toHaveLength(1);
  await page.getByRole("link", { name: "Back to schedules", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Offboarding schedules", exact: true }),
  ).toContainText("Cancelled");
  await expect(page.getByRole("link", { name: "Schedule offboarding", exact: true })).toBeVisible();
});
