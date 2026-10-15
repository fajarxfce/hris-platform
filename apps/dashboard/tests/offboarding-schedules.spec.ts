import { expect, type Page, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import { offboardingPermissions } from "./offboarding-api";
import {
  installOffboardingSchedulesApi,
  scheduledCaseId,
  scheduledId,
  scheduledJobId,
} from "./offboarding-schedules-api";

const casePath = `/people/lifecycle/cases/${scheduledCaseId}`;
const path = `${casePath}/offboarding-schedules`,
  filters = `company=${companyIds[0]}&status=OPEN`;
const departure = (page: Page) =>
  page.getByRole("dialog", { name: "Leave this page?", exact: true });
async function openReview(page: Page) {
  await page.goto(`${path}/new?${filters}`);
  await expect(page.getByRole("region", { name: "Review schedule", exact: true })).toContainText(
    "North employee 2",
  );
}
test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2026-10-01T03:00:00Z"));
});

test("an explicit schedule captures the reviewed basis and exposes retained detail through the case", async ({
  page,
}) => {
  const api = await installOffboardingSchedulesApi(page);
  await page.goto(`${casePath}?${filters}`);
  await page.getByRole("link", { name: "Offboarding schedules", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No offboarding schedules.");
  await page.getByRole("link", { name: "Schedule offboarding", exact: true }).click();
  await expect(page.getByRole("region", { name: "Review schedule", exact: true })).toContainText(
    "Oct 1, 2026",
  );
  await page.getByRole("button", { name: "Schedule offboarding", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("provide a reason");
  expect(api.writes).toHaveLength(0);
  await page.getByLabel("Reason", { exact: true }).fill("Departure reviewed");
  await page.getByRole("button", { name: "Schedule offboarding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Offboarding scheduled.");
  expect(api.writes).toHaveLength(1);
  expect(api.writes[0]?.body).toMatchObject({
    expectedVersion: 5,
    employmentVersion: 7,
    basisRevision: "a".repeat(64),
    reason: "Departure reviewed",
  });
  await page.getByRole("link", { name: "View schedule", exact: true }).click();
  await expect(page.getByRole("region", { name: "Schedule details", exact: true })).toContainText(
    "Scheduled",
  );
  await expect(page.getByRole("region", { name: "Schedule details", exact: true })).toContainText(
    "Asia/Jakarta",
  );
  expect(api.record.status).toBe("OPEN");
  expect(api.committed).toBe(1);
  const reads = api.scheduleReads.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(
    page.getByRole("heading", { name: "Detail jadwal offboarding", exact: true }),
  ).toBeVisible();
  expect(api.scheduleReads).toHaveLength(reads);
  expect(api.identity.unhandled).toEqual([]);
});
for (const missing of offboardingPermissions) {
  test(`direct scheduling requires ${missing} before HTTP`, async ({ page }) => {
    const api = await installOffboardingSchedulesApi(
      page,
      offboardingPermissions.filter((p) => p !== missing),
    );
    await page.goto(`${path}/new?${filters}`);
    await expect(page.getByRole("alert")).toContainText("You do not have access");
    await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
    expect(api.scheduleReads).toEqual([]);
    expect(api.writes).toEqual([]);
  });
}
test("server blockers prevent admission and review reload uses current versions", async ({
  page,
}) => {
  const api = await installOffboardingSchedulesApi(page);
  api.setBlockers(["required_lifecycle_tasks_pending", "reporting_reassignment_required"]);
  await openReview(page);
  await expect(page.getByRole("main")).toContainText("Complete every required task");
  await expect(page.getByRole("main")).toContainText(
    "Reassign current and scheduled reporting lines",
  );
  await expect(page.getByRole("button", { name: "Schedule offboarding", exact: true })).toHaveCount(
    0,
  );
  api.setBlockers([]);
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Original review");
  api.changeBasis();
  await page.getByRole("button", { name: "Schedule offboarding", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("timezone changed");
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Reason", { exact: true }).fill("Updated review");
  await page.getByRole("button", { name: "Schedule offboarding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Offboarding scheduled.");
  expect(api.writes[1]?.body).toMatchObject({
    expectedVersion: 6,
    employmentVersion: 8,
    basisRevision: "b".repeat(64),
  });
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
  expect(api.committed).toBe(1);
});
for (const action of ["schedule", "cancel", "recover"] as const) {
  test(`an uncertain ${action} survives MFA and a later conflict without replacing its command`, async ({
    page,
  }) => {
    const api = await installOffboardingSchedulesApi(page);
    if (action !== "schedule") api.items.push(api.seed());
    if (action === "recover") api.stopJob();
    const label =
      action === "schedule"
        ? "Schedule offboarding"
        : action === "cancel"
          ? "Cancel schedule"
          : "Close stopped attempt";
    const suffix = action === "schedule" ? "/new" : `/${scheduledId}/${action}`;
    await page.goto(`${path}${suffix}?${filters}`);
    await page.getByLabel("Reason", { exact: true }).fill("Reviewed command");
    api.loseCommand();
    await page.getByRole("button", { name: label, exact: true }).click();
    await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
    api.identity.expireMfa();
    api.rejectCommand("mfa_required", 403);
    await page.getByRole("button", { name: "Retry original command", exact: true }).click();
    await page
      .getByRole("alert")
      .getByRole("button", { name: "Account verification", exact: true })
      .click();
    const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
    await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
    await verification.getByRole("button", { name: "Verify", exact: true }).click();
    await expect(verification).toHaveCount(0);
    await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
    await expect(page.getByRole("region", { name: "Review schedule", exact: true })).toHaveCount(0);
    api.rejectCommand("offboarding_schedule_finished");
    await page.getByRole("button", { name: "Retry original command", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText("This schedule has finished.");
    await page.getByRole("button", { name: "Retry original command", exact: true }).click();
    await expect(page.getByRole("status")).toContainText(
      action === "schedule"
        ? "Offboarding scheduled."
        : action === "cancel"
          ? "Cancellation requested."
          : "Stopped attempt closed.",
    );
    expect(api.writes).toHaveLength(4);
    expect(api.committed).toBe(1);
    for (const write of api.writes) {
      expect(write.body).toEqual(api.writes[0]?.body);
      expect(write.operation).toBe(api.writes[0]?.operation);
    }
    await page.getByRole("link", { name: "View schedule", exact: true }).click();
    expect(page.url()).toContain(api.items[0]?.id ?? "UNREACHABLE");
    expect(page.url()).not.toContain(scheduledJobId);
  });
}
test("cancellation requests cleanup and preserves case history without ending employment", async ({
  page,
}) => {
  const api = await installOffboardingSchedulesApi(page);
  api.items.push(api.seed());
  await page.goto(`${path}/${scheduledId}?${filters}`);
  await page.getByRole("link", { name: "Cancel schedule", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Departure postponed");
  await page.getByRole("button", { name: "Cancel schedule", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Cancellation requested.");
  api.finishCancellation();
  await page.getByRole("link", { name: "View schedule", exact: true }).click();
  await expect(page.getByRole("region", { name: "Schedule details", exact: true })).toContainText(
    "Cancelled",
  );
  await expect(page.getByRole("link", { name: "Cancel schedule", exact: true })).toHaveCount(0);
  await expect(page.getByRole("alert")).toHaveCount(0);
  expect(api.record.status).toBe("OPEN");
  expect(api.writes[0]?.body).toEqual({
    expectedVersion: 0,
    expectedJobVersion: 0,
    reason: "Departure postponed",
  });
  await page.getByRole("link", { name: "Back to schedules", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Offboarding schedules", exact: true }),
  ).toContainText("Cancelled");
  await expect(page.getByRole("link", { name: "Schedule offboarding", exact: true })).toBeVisible();
});
test("a stopped job exposes explicit recovery and does not silently reschedule", async ({
  page,
}) => {
  const api = await installOffboardingSchedulesApi(page);
  api.items.push(api.seed());
  api.stopJob();
  await page.goto(`${path}/${scheduledId}?${filters}`);
  await expect(page.getByRole("region", { name: "Schedule details", exact: true })).toContainText(
    "Needs recovery",
  );
  await page.getByRole("link", { name: "Close stopped attempt", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Stopped job reviewed");
  await page.getByRole("button", { name: "Close stopped attempt", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Stopped attempt closed.");
  expect(api.writes[0]?.body).toEqual({
    expectedVersion: 0,
    expectedJobVersion: 4,
    reason: "Stopped job reviewed",
  });
  expect(api.items).toHaveLength(1);
  expect(api.items[0]?.status).toBe("FAILED");
  expect(api.record.status).toBe("OPEN");
});
test("company replacement cancels pending admission and ignores a late receipt", async ({
  page,
}) => {
  const api = await installOffboardingSchedulesApi(page);
  await openReview(page);
  await page.getByLabel("Reason", { exact: true }).fill("North departure");
  const held = api.holdScheduleCommand();
  try {
    await page.getByRole("button", { name: "Schedule offboarding", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page.getByRole("table", { name: "Cases", exact: true })).toContainText(
      "South employee",
    );
    held.release();
    await expect.poll(() => api.committed).toBe(1);
    await expect(page.getByRole("main")).not.toContainText("Offboarding scheduled.");
    await expect(page.getByRole("main")).not.toContainText("North employee");
    expect(api.writes).toHaveLength(1);
    expect(api.writes[0]?.company).toBe(companyIds[0]);
  } finally {
    held.release();
  }
});
test("late reads and denied refresh cannot expose another company or retain private details", async ({
  page,
}) => {
  const api = await installOffboardingSchedulesApi(page);
  api.items.push(api.seed());
  await page.goto(`${path}/${scheduledId}?${filters}`);
  await expect(page.getByRole("region", { name: "Schedule details", exact: true })).toBeVisible();
  api.rejectReads("offboarding_access_required");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  await expect(page.getByRole("region", { name: "Schedule details", exact: true })).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("PRIVATE");
  api.rejectReads(null);
  const held = api.holdScheduleRead();
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Cases", exact: true })).toContainText(
      "South employee",
    );
    held.release();
    await expect(page.getByRole("main")).not.toContainText("Departure reviewed");
  } finally {
    held.release();
  }
});
test("closed cases retain schedule navigation but offer no new admission", async ({ page }) => {
  const api = await installOffboardingSchedulesApi(page);
  api.items.push(api.seed());
  const current = api.items[0];
  if (!current) throw new Error("Expected schedule");
  current.job.cancellationRequested = true;
  api.finishCancellation();
  api.record.status = "CANCELLED";
  await page.goto(`${casePath}?${filters}`);
  await page.getByRole("link", { name: "Offboarding schedules", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Offboarding schedules", exact: true }),
  ).toContainText("Cancelled");
  await expect(page.getByRole("link", { name: "Schedule offboarding", exact: true })).toHaveCount(
    0,
  );
});
test("narrow dark Indonesian schedule review keeps actions and properties within the page", async ({
  page,
}) => {
  const api = await installOffboardingSchedulesApi(page);
  api.items.push(api.seed());
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${path}/${scheduledId}?${filters}`);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Detail jadwal offboarding", exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Batalkan jadwal", exact: true }).click();
  await page.getByLabel("Alasan", { exact: true }).fill("Rencana penugasan diperbarui");
  await expect(page.getByRole("button", { name: "Batalkan jadwal", exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "/tmp/hris-scheduled-offboarding/schedule-mobile-id.png",
    fullPage: true,
  });
  await page.evaluate(() => {
    document.documentElement.style.fontSize = "200%";
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
});
