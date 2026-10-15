import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import type {
  OnboardingScheduleDto,
  OnboardingScheduleReviewDto,
} from "../src/features/lifecycle/data/models/onboarding-schedule-dto";

test("scheduled onboarding recovers committed responses and cancels future creation through the real worker", async ({
  page,
  context,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-onboarding-admin@example.invalid",
    "ONBOARDING",
    "Scheduled Onboarding Company",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  const base = `/api/v1/companies/${company}`,
    headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const dateResponse = await context.request.get(
    `${base}/workforce/periods/${new Date().toISOString().slice(0, 7)}`,
  );
  expect(dateResponse.status()).toBe(200);
  const companyDate = ((await dateResponse.json()) as { companyDate: string }).companyDate;
  const day = (offset: number) =>
    new Date(Date.parse(`${companyDate}T00:00:00Z`) + offset * 86_400_000)
      .toISOString()
      .slice(0, 10);
  const firstDay = day(3),
    startsOn = day(1),
    employee = randomUUID(),
    template = randomUUID(),
    assignee = "a0000000-0000-4000-8000-000000000058";
  expect(
    (
      await context.request.put(`${base}/members/${assignee}`, {
        headers: headers(),
        data: {
          active: true,
          permissions: ["people.lifecycle.perform"],
          reason: "Joining coordinator fixture",
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
          employeeNumber: "JOINING-001",
          person: { id: randomUUID(), legalName: "Scheduled Joining Employee", nationality: "ID" },
          terms: {
            effectiveFrom: firstDay,
            startDate: firstDay,
            contract: "PERMANENT",
            status: "ACTIVE",
          },
          reason: "Future employment fixture",
        },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await context.request.put(`${base}/lifecycle/templates/${template}`, {
        headers: headers(),
        data: {
          code: "JOINING",
          name: "Joining preparation",
          kind: "ONBOARDING",
          active: true,
          tasks: [
            { key: "equipment", title: "Prepare equipment", required: true, dueDays: -1 },
            { key: "welcome", title: "Team introduction", required: false, dueDays: 0 },
          ],
          reason: "Joining checklist",
        },
      })
    ).status(),
  ).toBe(200);
  const scheduleBase = `${base}/employees/${employee}/onboarding-schedules`;
  const reviewResponse = await context.request.get(
    `${scheduleBase}/review?${new URLSearchParams({ templateId: template, targetDate: firstDay, startsOn })}`,
  );
  expect(reviewResponse.status()).toBe(200);
  const review = (await reviewResponse.json()) as OnboardingScheduleReviewDto;
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
  const path = `/people/employees/${employee}/onboarding-schedules`,
    filters = `company=${company}&asOf=${firstDay}`;
  await page.goto(`/people/employees/${employee}?${filters}`);
  await page.getByRole("link", { name: "Scheduled onboarding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No onboarding schedules.");
  await page.getByRole("link", { name: "Schedule onboarding", exact: true }).click();
  await expect(page.getByLabel("Preparation date", { exact: true })).toHaveValue(firstDay);
  await page.getByRole("button", { name: "Choose template", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Choose template", exact: true })
    .getByRole("button", { name: "Select: Joining preparation (JOINING)", exact: true })
    .click();
  await page.getByLabel("Preparation date", { exact: true }).fill(startsOn);
  await page.getByRole("button", { name: "Review schedule", exact: true }).click();
  await expect(page.getByRole("region", { name: "Review schedule", exact: true })).toContainText(
    "Scheduled Joining Employee",
  );
  await page
    .getByRole("table", { name: "Checklist", exact: true })
    .getByRole("button", { name: "Assign: Prepare equipment", exact: true })
    .click();
  await page
    .getByRole("dialog", { name: "Choose member", exact: true })
    .getByRole("button", {
      name: `Select: Browser Onboarding Coordinator · ${assignee}`,
      exact: true,
    })
    .click();
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed joining preparation");
  await page.getByRole("button", { name: "Schedule onboarding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Onboarding scheduled.");
  const directory = await context.request.get(scheduleBase);
  expect(directory.status()).toBe(200);
  const items = ((await directory.json()) as { items: OnboardingScheduleDto[] }).items;
  expect(items).toHaveLength(1);
  const schedule = items[0];
  if (!schedule) throw new Error("Expected schedule");
  expect(schedule).toMatchObject({
    version: 0,
    status: "SCHEDULED",
    targetDate: firstDay,
    startsOn,
    assignees: { equipment: assignee },
    job: { status: "QUEUED", cancellationRequested: false, completedItems: 0 },
  });
  expect(schedule.scheduledFor).toBe(review.scheduledFor);
  expect(writes[1]).toEqual(writes[0]);
  expect(writes[0]?.body).toMatchObject({
    basisRevision: review.basisRevision,
    employmentVersion: 0,
    templateVersion: 0,
  });
  await page.getByRole("link", { name: "View schedule", exact: true }).click();
  expect(page.url()).toContain(`${path}/${schedule.id}`);
  await expect(page.getByRole("region", { name: "Schedule details", exact: true })).toContainText(
    "Scheduled",
  );
  await expect(page.getByRole("link", { name: "View checklist", exact: true })).toHaveCount(0);
  await page.screenshot({
    path: "/tmp/hris-scheduled-onboarding/schedule-real-api.png",
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
  await page.getByLabel("Reason", { exact: true }).fill("Joining preparation postponed");
  await page.getByRole("button", { name: "Cancel schedule", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  await expect
    .poll(
      async () => {
        const result = await context.request.get(`${scheduleBase}/${schedule.id}`);
        expect(result.status()).toBe(200);
        return ((await result.json()) as OnboardingScheduleDto).status;
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
  const unchanged = await context.request.get(`${base}/employees/${employee}?asOf=${firstDay}`);
  expect(unchanged.status()).toBe(200);
  expect(await unchanged.json()).toMatchObject({
    version: 0,
    appliedRevision: 0,
    terms: { startDate: firstDay, status: "ACTIVE" },
  });
  const noCase = await context.request.get(`${base}/lifecycle/cases/${schedule.caseId}`);
  expect(noCase.status()).toBe(404);
  expect(((await noCase.json()) as { code: string }).code).toBe("lifecycle_case_not_found");
  const retained = await context.request.get(scheduleBase);
  expect(((await retained.json()) as { items: unknown[] }).items).toHaveLength(1);
  await page.getByRole("link", { name: "Back to schedules", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Scheduled onboarding", exact: true }),
  ).toContainText("Cancelled");
  await expect(page.getByRole("link", { name: "Schedule onboarding", exact: true })).toBeVisible();
});
