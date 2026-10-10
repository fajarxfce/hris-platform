import { expect, type Page, test } from "@playwright/test";
import { employeeImportPermissions, importId } from "./employee-import-api";
import { installEmployeeImportTransitionApi } from "./employee-import-transition-api";
import { companyIds } from "./identity-api";

const path = `/people/imports/${importId(0, 1)}`;
const query = `company=${companyIds[0]}`;
const partial = (page: Page) =>
  page.getByRole("checkbox", { name: "Apply ready rows and skip invalid rows", exact: true });
const departure = (page: Page) =>
  page.getByRole("dialog", { name: "Leave this page?", exact: true });
test("the dark mobile review retains entered fields across locale changes", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await installEmployeeImportTransitionApi(page);
  await open(page);
  await partial(page).check();
  await page.getByLabel("Reason", { exact: true }).fill("Review import bulanan");
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Review import bulanan");
  await expect(
    page.getByRole("checkbox", {
      name: "Terapkan baris siap dan lewati baris tidak valid",
      exact: true,
    }),
  ).toBeChecked();
  await expect(page.getByRole("region", { name: "Tinjau import", exact: true })).toContainText(
    "Berhasil",
  );
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-employee-import-apply-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
});
async function open(page: Page, action = "apply") {
  await page.goto(`${path}/${action}?${query}`);
  await expect(page.getByRole("region", { name: "Review import", exact: true })).toContainText(
    "north-1.csv",
  );
}
test("applying reviews counts and job state and requires explicit partial confirmation", async ({
  page,
}) => {
  const api = await installEmployeeImportTransitionApi(page);
  await page.goto(`${path}?${query}`);
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "Succeeded",
  );
  await page.getByRole("link", { name: "Apply import", exact: true }).click();
  const apply = page.getByRole("button", { name: "Apply import", exact: true });
  await expect(apply).toBeDisabled();
  await expect(page.getByRole("alert")).toContainText("Some rows are invalid");
  await partial(page).check();
  await apply.click();
  await expect(page.getByRole("alert")).toContainText("provide a reason");
  expect(api.writes).toEqual([]);
  await page.getByLabel("Reason", { exact: true }).fill("  Intake reviewed  ");
  await apply.click();
  await expect(page.getByRole("status")).toContainText("Import queued.");
  expect(api.writes[0]?.body).toEqual({
    expectedVersion: 1,
    reason: "Intake reviewed",
    allowPartial: true,
  });
  await expect(page.getByRole("region", { name: "Review import", exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "Open import", exact: true }).click();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText("Queued");
  await expect(page.getByRole("link", { name: "Apply import", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Cancel import", exact: true })).toBeVisible();
  expect(api.committed).toBe(1);
  expect(api.identity.unhandled).toEqual([]);
});
test("cancel acknowledges a running job request and resume is offered only after it stops", async ({
  page,
}) => {
  const api = await installEmployeeImportTransitionApi(page);
  const record = api.records.get(companyIds[0])?.[0];
  const context = api.contexts.get(importId(0, 1));
  if (!record || !context) throw new Error("Expected import fixture");
  record.status = "IMPORTING";
  context.jobStatus = "RUNNING";
  context.availableActions = ["cancel"];
  await open(page, "cancel");
  await page.getByLabel("Reason", { exact: true }).fill("Pause intake");
  await page.getByRole("button", { name: "Cancel import", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Cancellation requested.");
  await page.getByRole("link", { name: "Open import", exact: true }).click();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "Running",
  );
  await expect(page.getByRole("link", { name: "Resume import", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Cancel import", exact: true })).toHaveCount(0);
  record.status = "STOPPED";
  context.jobStatus = "CANCELLED";
  context.availableActions = ["resume", "cancel"];
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await page.getByRole("link", { name: "Resume import", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Intake can continue");
  await page.getByRole("button", { name: "Resume import", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Resume queued.");
  expect(api.writes.map((write) => write.body)).toEqual([
    { expectedVersion: 1, reason: "Pause intake" },
    { expectedVersion: 2, reason: "Intake can continue" },
  ]);
  expect(api.committed).toBe(2);
});
test("an uncertain apply retains its original confirmation and operation through MFA and a terminal reply", async ({
  page,
}) => {
  const api = await installEmployeeImportTransitionApi(page);
  await open(page);
  await partial(page).check();
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed original file");
  const reads = api.reads.length;
  api.lose();
  await page.getByRole("button", { name: "Apply import", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The outcome is not confirmed.");
  await expect(partial(page)).toBeDisabled();
  await expect(partial(page)).toBeChecked();
  api.identity.expireMfa();
  api.reject("mfa_required", 403);
  await page.getByRole("button", { name: "Retry original request", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(partial(page)).toBeChecked();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Reviewed original file");
  api.reject("employee_import_is_terminal");
  await page.getByRole("button", { name: "Retry original request", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("already completed or cancelled");
  await page.getByRole("button", { name: "Retry original request", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Import queued.");
  expect(api.reads).toHaveLength(reads);
  expect(api.writes).toHaveLength(4);
  for (const write of api.writes) {
    expect(write.operation).toBe(api.writes[0]?.operation);
    expect(write.body).toEqual(api.writes[0]?.body);
  }
  expect(api.committed).toBe(1);
  expect(new Set(api.writes.map((write) => write.csrf)).size).toBe(4);
});
test("stale versions require a protected fresh review and reset the partial confirmation", async ({
  page,
}) => {
  const api = await installEmployeeImportTransitionApi(page);
  await open(page);
  await partial(page).check();
  await page.getByLabel("Reason", { exact: true }).fill("Original review");
  const record = api.records.get(companyIds[0])?.[0];
  if (!record) throw new Error("Expected import fixture");
  record.version++;
  await page.getByRole("button", { name: "Apply import", exact: true }).click();
  await expect(page.getByRole("button", { name: "Apply import", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(page.getByRole("button", { name: "Reload review", exact: true })).toBeFocused();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(partial(page)).not.toBeChecked();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await partial(page).check();
  await page.getByLabel("Reason", { exact: true }).fill("New review");
  await page.getByRole("button", { name: "Apply import", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Import queued.");
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([1, 2]);
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
});
for (const action of ["apply", "resume", "cancel"])
  test(`direct ${action} links cannot bypass a terminal summary`, async ({ page }) => {
    const api = await installEmployeeImportTransitionApi(page);
    const context = api.contexts.get(importId(0, 1));
    if (!context) throw new Error("Expected fixture");
    context.availableActions = [];
    await open(page, action);
    await expect(page.getByRole("alert")).toContainText("This action is unavailable");
    await expect(
      page.getByRole("button", {
        name: `${action[0]?.toUpperCase()}${action.slice(1)} import`,
        exact: true,
      }),
    ).toBeDisabled();
    expect(api.writes).toEqual([]);
  });
for (const missing of employeeImportPermissions)
  test(`import transitions require ${missing} before private reads`, async ({ page }) => {
    const api = await installEmployeeImportTransitionApi(
      page,
      employeeImportPermissions.filter((p) => p !== missing),
    );
    await page.goto(`${path}/apply?${query}`);
    await expect(page.getByRole("alert")).toContainText(
      "You do not have access to employee imports",
    );
    expect(api.reads).toEqual([]);
    expect(api.writes).toEqual([]);
  });
test("switching company disposes a pending apply and cannot display its late receipt", async ({
  page,
}) => {
  const api = await installEmployeeImportTransitionApi(page);
  await open(page);
  await partial(page).check();
  await page.getByLabel("Reason", { exact: true }).fill("North intake");
  const pending = api.holdWrite();
  await page.getByRole("button", { name: "Apply import", exact: true }).click();
  await pending.entered;
  try {
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page).toHaveURL(`/people/imports?company=${companyIds[1]}`);
  } finally {
    pending.release();
  }
  await expect(page.getByRole("table")).toContainText("south-1.csv");
  await expect(page.getByText("Import queued.", { exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(1);
  expect(
    api.reads
      .filter((url) => url.pathname.includes(companyIds[1]))
      .every((url) => !url.pathname.includes(importId(0, 1))),
  ).toBe(true);
});
