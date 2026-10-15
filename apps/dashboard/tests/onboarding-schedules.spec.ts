import { expect, type Page, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import {
  assigneeId,
  installOnboardingSchedulesApi,
  onboardingPermissions,
  scheduledEmployee,
  scheduledId,
  scheduledJobId,
} from "./onboarding-schedules-api";

const employeePath = `/people/employees/${scheduledEmployee}`,
  path = `${employeePath}/onboarding-schedules`,
  filters = `company=${companyIds[0]}&asOf=2026-10-04&query=Joining`;
const templatePicker = (page: Page) =>
  page.getByRole("dialog", { name: "Choose template", exact: true });
const departure = (page: Page) =>
  page.getByRole("dialog", { name: "Leave this page?", exact: true });
async function chooseTemplate(page: Page) {
  await page.getByRole("button", { name: "Choose template", exact: true }).click();
  await templatePicker(page)
    .getByRole("button", { name: "Select: North checklist 1 (TEMPLATE_001)", exact: true })
    .click();
}
async function prepare(page: Page) {
  await chooseTemplate(page);
  await page.getByLabel("Preparation date", { exact: true }).fill("2026-10-02");
  await page.getByRole("button", { name: "Review schedule", exact: true }).click();
  await expect(page.getByRole("region", { name: "Review schedule", exact: true })).toContainText(
    "North joining employee",
  );
}
async function openReview(page: Page) {
  await page.goto(`${path}/new?${filters}`);
  await prepare(page);
}
test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2026-10-01T03:00:00Z"));
});

test("employee discovery leads to reviewed scheduling with explicit optional task assignments", async ({
  page,
}) => {
  const api = await installOnboardingSchedulesApi(page);
  await page.goto(`${employeePath}?${filters}`);
  await page.getByRole("link", { name: "Scheduled onboarding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No onboarding schedules.");
  await page.getByRole("link", { name: "Schedule onboarding", exact: true }).click();
  await expect(page.getByLabel("Preparation date", { exact: true })).toHaveValue("2026-10-04");
  expect(api.reads).toEqual([]);
  expect(api.assigneeReads).toEqual([]);
  await prepare(page);
  const checklist = page.getByRole("table", { name: "Checklist", exact: true });
  await expect(checklist).toContainText("Oct 2, 2026");
  await checklist.getByRole("button", { name: "Assign: Review equipment", exact: true }).click();
  const members = page.getByRole("dialog", { name: "Choose member", exact: true });
  await members
    .getByRole("button", { name: `Select: Joining coordinator · ${assigneeId}`, exact: true })
    .click();
  await expect(checklist).toContainText("Joining coordinator");
  await checklist.getByRole("button", { name: "Assign: Review equipment", exact: true }).click();
  await members.getByRole("button", { name: "Remove assignee", exact: true }).click();
  await expect(checklist).not.toContainText("Joining coordinator");
  await checklist.getByRole("button", { name: "Assign: Review equipment", exact: true }).click();
  await members
    .getByRole("button", { name: `Select: Joining coordinator · ${assigneeId}`, exact: true })
    .click();
  await page.getByRole("button", { name: "Schedule onboarding", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("reviewed checklist and reason");
  expect(api.writes).toHaveLength(0);
  await page.getByLabel("Reason", { exact: true }).fill("Joining preparation reviewed");
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "/tmp/hris-scheduled-onboarding/review-desktop.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Schedule onboarding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Onboarding scheduled.");
  expect(api.writes[0]?.body).toMatchObject({
    employmentVersion: 7,
    templateVersion: 2,
    basisRevision: "a".repeat(64),
    targetDate: "2026-10-04",
    startsOn: "2026-10-02",
    assignees: { equipment: assigneeId },
    reason: "Joining preparation reviewed",
  });
  expect(api.writes[0]?.body.id).not.toBe(api.writes[0]?.body.caseId);
  expect(api.committed).toBe(1);
  expect(api.caseCommits).toBe(0);
  await page.getByRole("link", { name: "View schedule", exact: true }).click();
  await expect(page.getByRole("region", { name: "Schedule details", exact: true })).toContainText(
    "Scheduled",
  );
  await expect(page.getByRole("link", { name: "View checklist", exact: true })).toHaveCount(0);
  const reads = api.scheduleReads.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByRole("heading", { name: "Jadwal onboarding", exact: true })).toBeVisible();
  expect(api.scheduleReads).toHaveLength(reads);
  expect(api.identity.unhandled).toEqual([]);
});
test("template picker limits selection to active onboarding definitions without scanning", async ({
  page,
}) => {
  const api = await installOnboardingSchedulesApi(page),
    other = api.templates.get(companyIds[0])?.[1];
  if (!other) throw new Error("Expected offboarding template");
  other.active = true;
  await page.goto(`${path}/new?${filters}`);
  await page.getByRole("button", { name: "Choose template", exact: true }).click();
  await expect(
    templatePicker(page).getByRole("button", {
      name: "Select: North checklist 2 (TEMPLATE_002)",
      exact: true,
    }),
  ).toHaveCount(0);
  await expect(templatePicker(page).getByRole("table").getByRole("row")).toHaveCount(11);
  expect(api.reads).toHaveLength(1);
  await templatePicker(page).getByRole("button", { name: "Next page", exact: true }).click();
  await expect(templatePicker(page).getByRole("table").getByRole("row")).toHaveCount(2);
  expect(api.reads).toHaveLength(2);
});
for (const missing of onboardingPermissions) {
  test(`schedule entry requires ${missing} before private acquisition`, async ({ page }) => {
    const api = await installOnboardingSchedulesApi(
      page,
      onboardingPermissions.filter((p) => p !== missing),
    );
    await page.goto(`${path}/new?${filters}`);
    await expect(page.getByRole("alert")).toContainText(
      "Onboarding schedule access is unavailable.",
    );
    expect(api.employeeReads).toEqual([]);
    expect(api.scheduleReads).toEqual([]);
    expect(api.writes).toEqual([]);
    if (missing !== "people.read") {
      await page.goto(`${employeePath}?${filters}`);
      await expect(
        page.getByRole("heading", { name: "North joining employee", exact: true }),
      ).toBeVisible();
      await expect(
        page.getByRole("link", { name: "Scheduled onboarding", exact: true }),
      ).toHaveCount(0);
    }
  });
}
test("blockers prevent admission and a changed source needs a deliberate new review", async ({
  page,
}) => {
  const api = await installOnboardingSchedulesApi(page);
  api.setBlockers(["onboarding_already_completed"]);
  await openReview(page);
  await expect(page.getByRole("alert")).toContainText("already completed onboarding");
  await expect(page.getByRole("button", { name: "Schedule onboarding", exact: true })).toHaveCount(
    0,
  );
  api.setBlockers([]);
  await page.getByRole("button", { name: "Change selection", exact: true }).click();
  await page.getByRole("button", { name: "Review schedule", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Original review");
  api.changeBasis();
  await page.getByRole("button", { name: "Schedule onboarding", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("timezone changed");
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await prepare(page);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Reason", { exact: true }).fill("Updated review");
  await page.getByRole("button", { name: "Schedule onboarding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Onboarding scheduled.");
  expect(api.writes[1]?.body).toMatchObject({
    employmentVersion: 8,
    templateVersion: 3,
    basisRevision: "b".repeat(64),
  });
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
  expect(api.committed).toBe(1);
});
for (const action of ["schedule", "cancel", "recover"] as const) {
  test(`uncertain ${action} retains its exact payload through MFA and later conflicts`, async ({
    page,
  }) => {
    const api = await installOnboardingSchedulesApi(page);
    if (action !== "schedule") api.items.push(api.seed());
    if (action === "recover") api.stopJob();
    if (action === "schedule") await openReview(page);
    else await page.goto(`${path}/${scheduledId}/${action}?${filters}`);
    const label =
      action === "schedule"
        ? "Schedule onboarding"
        : action === "cancel"
          ? "Cancel schedule"
          : "Close stopped schedule";
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
    api.rejectCommand("onboarding_schedule_finished");
    await page.getByRole("button", { name: "Retry original command", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText("This schedule has finished.");
    await page.getByRole("button", { name: "Retry original command", exact: true }).click();
    await expect(page.getByRole("status")).toContainText(
      action === "schedule"
        ? "Onboarding scheduled."
        : action === "cancel"
          ? "Cancellation requested."
          : "Stopped schedule closed.",
    );
    expect(api.writes).toHaveLength(4);
    expect(api.committed).toBe(1);
    for (const write of api.writes) {
      expect(write.body).toEqual(api.writes[0]?.body);
      expect(write.operation).toBe(api.writes[0]?.operation);
    }
    await page.getByRole("link", { name: "View schedule", exact: true }).click();
    expect(page.url()).toContain(api.items[0]?.id ?? "MISSING");
    expect(page.url()).not.toContain(scheduledJobId);
  });
}
test("cancellation requests cleanup without creating a case or modifying employment", async ({
  page,
}) => {
  const api = await installOnboardingSchedulesApi(page);
  api.items.push(api.seed());
  await page.goto(`${path}/${scheduledId}?${filters}`);
  await page.getByRole("link", { name: "Cancel schedule", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Joining postponed");
  await page.getByRole("button", { name: "Cancel schedule", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Cancellation requested.");
  api.finishCancellation();
  await page.getByRole("link", { name: "View schedule", exact: true }).click();
  await expect(page.getByRole("region", { name: "Schedule details", exact: true })).toContainText(
    "Cancelled",
  );
  await expect(page.getByRole("alert")).toHaveCount(0);
  expect(api.writes[0]?.body).toEqual({
    expectedVersion: 0,
    expectedJobVersion: 0,
    reason: "Joining postponed",
  });
  expect(api.cases.get(companyIds[0])?.size).toBe(0);
  expect(api.employee.version).toBe(7);
  await page.getByRole("link", { name: "Back to schedules", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Scheduled onboarding", exact: true }),
  ).toContainText("Cancelled");
  await expect(page.getByRole("link", { name: "Schedule onboarding", exact: true })).toBeVisible();
});
test("terminal job recovery closes retained evidence without rescheduling", async ({ page }) => {
  const api = await installOnboardingSchedulesApi(page);
  api.items.push(api.seed());
  api.stopJob();
  await page.goto(`${path}/${scheduledId}?${filters}`);
  await expect(page.getByRole("region", { name: "Schedule details", exact: true })).toContainText(
    "Needs recovery",
  );
  await page.getByRole("link", { name: "Close stopped schedule", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Stopped job reviewed");
  await page.getByRole("button", { name: "Close stopped schedule", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Stopped schedule closed.");
  expect(api.items).toHaveLength(1);
  expect(api.items[0]?.status).toBe("FAILED");
  expect(api.cases.get(companyIds[0])?.size).toBe(0);
  expect(api.writes[0]?.body.expectedJobVersion).toBe(4);
});
test("completed initiation links to an open checklist with pending tasks", async ({ page }) => {
  const api = await installOnboardingSchedulesApi(page);
  api.items.push(api.seed());
  api.finishInitiation();
  await page.goto(`${path}/${scheduledId}?${filters}`);
  await expect(page.getByRole("region", { name: "Schedule details", exact: true })).toContainText(
    "Checklist created",
  );
  await expect(page.getByRole("link", { name: "Cancel schedule", exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "View checklist", exact: true }).click();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText("Open");
  await expect(page.getByRole("table", { name: "Checklist", exact: true })).toContainText(
    "Pending",
  );
  expect(api.writes).toEqual([]);
});
test("company departure aborts pending admission and discards a late receipt", async ({ page }) => {
  const api = await installOnboardingSchedulesApi(page);
  await openReview(page);
  await page.getByLabel("Reason", { exact: true }).fill("North joining plan");
  const held = api.holdScheduleCommand();
  try {
    await page.getByRole("button", { name: "Schedule onboarding", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(
      page.getByRole("table", { name: "Employee directory", exact: true }),
    ).toContainText("South employee");
    held.release();
    await expect.poll(() => api.committed).toBe(1);
    await expect(page.getByRole("main")).not.toContainText("North joining employee");
    await expect(page.getByRole("main")).not.toContainText("Onboarding scheduled.");
    expect(api.writes).toHaveLength(1);
  } finally {
    held.release();
  }
});
test("denied refresh removes retained data and late reads cannot cross companies", async ({
  page,
}) => {
  const api = await installOnboardingSchedulesApi(page);
  api.items.push(api.seed());
  await page.goto(`${path}/${scheduledId}?${filters}`);
  await expect(page.getByRole("region", { name: "Schedule details", exact: true })).toBeVisible();
  api.rejectReads("onboarding_access_required");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Onboarding schedule access is unavailable.");
  await expect(page.getByRole("region", { name: "Schedule details", exact: true })).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("PRIVATE");
  api.rejectReads(null);
  const held = api.holdScheduleRead();
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(
      page.getByRole("table", { name: "Employee directory", exact: true }),
    ).toContainText("South employee");
    held.release();
    await expect(page.getByRole("main")).not.toContainText("Joining plan reviewed");
  } finally {
    held.release();
  }
});
test("narrow dark Indonesian review preserves input through locale and theme changes", async ({
  page,
}) => {
  const api = await installOnboardingSchedulesApi(page);
  await openReview(page);
  await page.getByLabel("Reason", { exact: true }).fill("Persiapan karyawan ditinjau");
  const reads = api.scheduleReads.length;
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue(
    "Persiapan karyawan ditinjau",
  );
  await expect(
    page.getByRole("button", { name: "Jadwalkan onboarding", exact: true }),
  ).toBeVisible();
  expect(api.scheduleReads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "/tmp/hris-scheduled-onboarding/review-mobile-id.png",
    fullPage: true,
  });
  await page.evaluate(() => {
    document.documentElement.style.fontSize = "200%";
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
});
