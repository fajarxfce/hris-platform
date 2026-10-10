import { expect, type Page, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import { lifecycleCaseId } from "./lifecycle-cases-api";
import { installOffboardingApi, offboardingPermissions } from "./offboarding-api";

const casePath = `/people/lifecycle/cases/${lifecycleCaseId(0, 2)}`;
const filters = `company=${companyIds[0]}&status=OPEN`;
const route = `${casePath}/offboarding?${filters}`;
const departure = (page: Page) =>
  page.getByRole("dialog", { name: "Leave this page?", exact: true });
async function open(page: Page) {
  await page.goto(route);
  await expect(page.getByRole("region", { name: "Case details", exact: true })).toContainText(
    "North employee 2",
  );
}

test("offboarding reviews both versions and company date, then opens the completed case explicitly", async ({
  page,
}) => {
  const api = await installOffboardingApi(page);
  api.resolveTasks();
  await page.clock.setFixedTime(new Date("2040-01-01T00:00:00Z"));
  await page.goto(`${casePath}?${filters}`);
  await page.getByRole("link", { name: "Review offboarding", exact: true }).click();
  await expect(page.getByRole("region", { name: "Review offboarding", exact: true })).toContainText(
    "Oct 2, 2026",
  );
  await expect(page.getByRole("table", { name: "Checklist", exact: true })).toContainText("Waived");
  await page.getByRole("button", { name: "Complete offboarding", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("provide a reason");
  expect(api.completions).toEqual([]);
  const reads = api.reviews.length;
  await page.getByLabel("Reason", { exact: true }).fill("Departure reviewed");
  await page.getByRole("button", { name: "Complete offboarding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Offboarding completed.");
  await expect(page.getByRole("region", { name: "Case details", exact: true })).toHaveCount(0);
  expect(api.completions[0]?.body).toEqual({
    expectedVersion: 5,
    employmentVersion: 7,
    reason: "Departure reviewed",
  });
  expect(api.reviews).toHaveLength(reads);
  expect(api.completionsCommitted).toBe(1);
  await page.getByRole("link", { name: "Open case", exact: true }).click();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "Completed",
  );
  await expect(page.getByRole("link", { name: "Review offboarding", exact: true })).toHaveCount(0);
  await page.getByRole("tab", { name: "History", exact: true }).click();
  await page.getByRole("button", { name: "View change: 6", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Change details", exact: true })).toContainText(
    "Departure reviewed",
  );
  expect(api.identity.unhandled).toEqual([]);
  expect(api.reads.some((url) => url.pathname.includes("/employees/"))).toBe(false);
});

for (const missing of offboardingPermissions) {
  test(`direct offboarding links require ${missing} before acquiring a review`, async ({
    page,
  }) => {
    const api = await installOffboardingApi(
      page,
      offboardingPermissions.filter((permission) => permission !== missing),
    );
    await page.goto(route);
    await expect(page.getByRole("alert")).toContainText(
      "You do not have access to review or complete offboarding.",
    );
    await expect(
      page.getByRole("button", { name: "Complete offboarding", exact: true }),
    ).toHaveCount(0);
    expect(api.reviews).toEqual([]);
    expect(api.completions).toEqual([]);
  });
}
for (const blocked of ["required", "optional", "date"]) {
  test(`a ${blocked} blocker needs an explicit fresh review before completion`, async ({
    page,
  }) => {
    const api = await installOffboardingApi(page);
    api.resolveTasks();
    if (blocked === "date") api.setCompanyDate("2026-10-01");
    else api.reopenTask(blocked === "required" ? "equipment" : "welcome");
    await open(page);
    await expect(page.getByRole("alert")).toContainText(
      blocked === "date"
        ? "after the last working date"
        : blocked === "required"
          ? "Complete every required task"
          : "Resolve the remaining tasks",
    );
    await expect(
      page.getByRole("button", { name: "Complete offboarding", exact: true }),
    ).toBeDisabled();
    expect(api.completions).toEqual([]);
    api.resolveTasks();
    api.setCompanyDate("2026-10-02");
    await page.getByRole("button", { name: "Reload review", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Complete offboarding", exact: true }),
    ).toBeEnabled();
    await expect(page.getByRole("alert")).toHaveCount(0);
  });
}

test("a changed employment requires a protected reload and new observed versions", async ({
  page,
}) => {
  const api = await installOffboardingApi(page);
  api.resolveTasks();
  await open(page);
  await page.getByLabel("Reason", { exact: true }).fill("Departure reviewed");
  api.advanceEmployment();
  await page.getByRole("button", { name: "Complete offboarding", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This employment has changed.");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveAttribute("readonly", "");
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(page.getByRole("button", { name: "Reload review", exact: true })).toBeFocused();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Reason", { exact: true }).fill("Employment revision reviewed");
  await page.getByRole("button", { name: "Complete offboarding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Offboarding completed.");
  expect(api.completions.map((write) => write.body.employmentVersion)).toEqual([7, 8]);
  expect(api.completions[1]?.operation).not.toBe(api.completions[0]?.operation);
  expect(api.completionsCommitted).toBe(1);
});

test("uncertain offboarding retains its original command through MFA and a closed case reply", async ({
  page,
}) => {
  const api = await installOffboardingApi(page);
  api.resolveTasks();
  await open(page);
  const reads = api.reviews.length;
  await page.getByLabel("Reason", { exact: true }).fill("Departure reviewed");
  api.loseCompletion();
  await page.getByRole("button", { name: "Complete offboarding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The result is unconfirmed.");
  api.identity.expireMfa();
  api.rejectCompletion("mfa_required", 403);
  await page.getByRole("button", { name: "Retry original completion", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Departure reviewed");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveAttribute("readonly", "");
  api.rejectCompletion("lifecycle_case_not_open");
  await page.getByRole("button", { name: "Retry original completion", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This case is no longer open.");
  await page.getByRole("button", { name: "Retry original completion", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Offboarding completed.");
  expect(api.reviews).toHaveLength(reads);
  expect(api.completions).toHaveLength(4);
  for (const write of api.completions) {
    expect(write.body).toEqual(api.completions[0]?.body);
    expect(write.operation).toBe(api.completions[0]?.operation);
  }
  expect(new Set(api.completions.map((write) => write.csrf)).size).toBe(4);
  expect(api.completionsCommitted).toBe(1);
});

for (const [code, message] of [
  ["scheduled_employment_changes_pending", "Resolve the scheduled employment revisions"],
  ["reporting_reassignment_required", "Reassign current and scheduled reporting lines"],
  ["cannot_complete_own_offboarding", "Another authorized operator"],
  ["last_company_administrator", "Assign another active company administrator"],
]) {
  test(`server ${code} policy prevents completion without exposing diagnostics`, async ({
    page,
  }) => {
    const api = await installOffboardingApi(page);
    api.resolveTasks();
    await open(page);
    api.rejectCompletion(code ?? "unexpected_error");
    await page.getByLabel("Reason", { exact: true }).fill("Departure reviewed");
    await page.getByRole("button", { name: "Complete offboarding", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText(message ?? "");
    await expect(page.getByRole("main")).not.toContainText("PRIVATE");
    await expect(
      page.getByRole("button", { name: "Complete offboarding", exact: true }),
    ).toBeDisabled();
    await expect(page.getByRole("button", { name: "Reload review", exact: true })).toBeEnabled();
    expect(api.completionsCommitted).toBe(0);
  });
}

test("company departure cancels the pending completion and ignores a late receipt", async ({
  page,
}) => {
  const api = await installOffboardingApi(page);
  api.resolveTasks();
  await open(page);
  await page.getByLabel("Reason", { exact: true }).fill("Departure reviewed");
  const held = api.holdCompletion();
  try {
    await page.getByRole("button", { name: "Complete offboarding", exact: true }).click();
    await held.entered;
    await expect(
      page.getByRole("button", { name: "Complete offboarding", exact: true }),
    ).toBeDisabled();
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
    await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Departure reviewed");
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page.getByRole("table", { name: "Cases", exact: true })).toContainText(
      "South employee 1",
    );
    held.release();
    await expect.poll(() => api.completionsCommitted).toBe(1);
    await expect(page.getByRole("main")).not.toContainText("North employee");
    await expect(page.getByRole("main")).not.toContainText("Offboarding completed.");
    expect(api.completions).toHaveLength(1);
  } finally {
    held.release();
  }
});

test("a late review cannot enter another company and a rejected refresh removes prior context", async ({
  page,
}) => {
  const api = await installOffboardingApi(page);
  api.resolveTasks();
  await open(page);
  api.rejectReview("offboarding_access_required");
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "You do not have access to review or complete offboarding.",
  );
  await expect(page.getByRole("main")).not.toContainText("North employee");
  await expect(page.getByRole("main")).not.toContainText("PRIVATE");
  api.rejectReview(null);
  const held = api.holdReview();
  try {
    await page.getByRole("button", { name: "Retry", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Cases", exact: true })).toContainText(
      "South employee 1",
    );
    held.release();
    await expect(page.getByRole("main")).not.toContainText("North employee");
    expect(api.completions).toEqual([]);
  } finally {
    held.release();
  }
});

test("the Indonesian offboarding review retains draft fields and fits dark mobile layouts", async ({
  page,
}) => {
  const api = await installOffboardingApi(page);
  api.resolveTasks();
  await open(page);
  await page.getByLabel("Reason", { exact: true }).fill("Departure reviewed");
  const reads = api.reviews.length;
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Departure reviewed");
  await expect(page.getByRole("main")).toContainText("Hari kerja terakhir");
  await expect(page.getByRole("main")).toContainText("Akses perusahaan dinonaktifkan");
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-offboarding-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(api.reviews).toHaveLength(reads);
  await page.getByRole("button", { name: "Selesaikan offboarding", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Offboarding selesai.");
  expect(api.completionsCommitted).toBe(1);
});
