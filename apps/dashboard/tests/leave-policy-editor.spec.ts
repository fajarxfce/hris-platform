import { expect, type Page, test } from "@playwright/test";
import { leavePolicyId } from "./fixtures/leave-policies";
import { companyIds } from "./identity-api";
import { installLeavePolicyEditorApi } from "./leave-policy-editor-api";

const query = `company=${companyIds[0]}`;
const directory = `/leave/policies?${query}`;
const editor = `/leave/policies/${leavePolicyId()}/edit?${query}`;
async function openEditor(page: Page) {
  await page.goto(editor);
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("North leave 01");
  await page.getByLabel("Reason", { exact: true }).fill("Annual policy review");
}

test("create policy saves normalized terms and opens its immutable first revision", async ({
  page,
}) => {
  const api = await installLeavePolicyEditorApi(page);
  await page.goto(directory);
  await page.getByRole("link", { name: "Create policy", exact: true }).click();
  await page.getByLabel("Code", { exact: true }).fill("annual_ui");
  await page.getByLabel("Name", { exact: true }).fill("Annual allowance");
  await page.getByLabel("Effective from", { exact: true }).fill("2027-01-01");
  await page.getByLabel("Minimum service (months)", { exact: true }).fill("12");
  await page.getByLabel("Maximum days per request", { exact: true }).fill("15");
  await page.getByRole("checkbox", { name: "Fixed term", exact: true }).uncheck();
  await page.getByRole("checkbox", { name: "Evidence required", exact: true }).check();
  await page.getByRole("combobox", { name: "Entitlement", exact: true }).selectOption("ANNUAL");
  await page.getByLabel("Days per period", { exact: true }).fill("12.5");
  await page.getByLabel("Carryover limit (days)", { exact: true }).fill("3.5");
  await page.getByLabel("Reason", { exact: true }).fill("Annual benefit policy");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  expect(api.writes).toHaveLength(1);
  expect(api.writes[0]?.body).toMatchObject({
    code: "ANNUAL_UI",
    expectedVersion: null,
    minServiceMonths: 12,
    maxRequestDays: 15,
    allowedContracts: ["PERMANENT"],
    attachmentRequired: true,
    accrual: { frequency: "ANNUAL", daysPerPeriod: "12.5", carryLimitDays: "3.5" },
  });
  await page.getByRole("link", { name: "View policy", exact: true }).click();
  await page.getByRole("button", { name: "View: Revision 0", exact: true }).click();
  await expect(page.getByRole("region", { name: "Revision 0", exact: true })).toContainText(
    "Annual benefit policy",
  );
});

test("editing from old history uses the latest saved policy and keeps its code immutable", async ({
  page,
}) => {
  const api = await installLeavePolicyEditorApi(page);
  await page.goto(`/leave/policies/${leavePolicyId()}?${query}&historyAfter=4`);
  await page.getByRole("button", { name: "View: Revision 3", exact: true }).click();
  await page.getByRole("link", { name: "Edit policy", exact: true }).click();
  await expect(page).toHaveURL(editor);
  await expect(page.getByLabel("Code", { exact: true })).toHaveAttribute("readonly", "");
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("North leave 01");
  await page.getByLabel("Name", { exact: true }).fill("Updated North policy");
  await page.getByRole("checkbox", { name: "Enabled", exact: true }).check();
  await page.getByLabel("Reason", { exact: true }).fill("Current policy review");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  expect(api.writes[0]?.body).toMatchObject({
    expectedVersion: 23,
    code: "LEAVE001",
    name: "Updated North policy",
    active: true,
  });
  await page.getByRole("link", { name: "View policy", exact: true }).click();
  await page.getByRole("button", { name: "View: Revision 23", exact: true }).click();
  await expect(page.getByRole("region", { name: "Revision 23", exact: true })).toContainText(
    "North leave 01",
  );
});

test("field validation blocks invalid half days and missing eligible contracts before HTTP", async ({
  page,
}) => {
  const api = await installLeavePolicyEditorApi(page);
  await openEditor(page);
  await page.getByLabel("Days per period", { exact: true }).fill("1.25");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("alert").filter({ hasText: "Check the entitlement" })).toBeVisible();
  await expect(page.getByLabel("Days per period", { exact: true })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(api.writes).toHaveLength(0);
  await page.getByLabel("Days per period", { exact: true }).fill("1.5");
  await page.getByRole("checkbox", { name: "Partial days", exact: true }).uncheck();
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  expect(api.writes).toHaveLength(0);
  await page.getByRole("checkbox", { name: "Partial days", exact: true }).check();
  await page.getByRole("checkbox", { name: "Permanent", exact: true }).uncheck();
  await page.getByRole("checkbox", { name: "Fixed term", exact: true }).uncheck();
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByText("Select at least one contract type.", { exact: true })).toBeVisible();
  expect(api.writes).toHaveLength(0);
  await page.getByRole("checkbox", { name: "Permanent", exact: true }).check();
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
});

for (const mode of ["MANUAL", "NONE"] as const)
  test(`${mode} entitlement excludes hidden periodic inputs from the saved command`, async ({
    page,
  }) => {
    const api = await installLeavePolicyEditorApi(page);
    await openEditor(page);
    await page.getByRole("combobox", { name: "Entitlement", exact: true }).selectOption(mode);
    await expect(page.getByLabel("Days per period", { exact: true })).toHaveCount(0);
    await page.getByRole("button", { name: "Save policy", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Policy saved.");
    expect(api.writes[0]?.body.accrual).toEqual(
      mode === "NONE" ? null : { frequency: "MANUAL", daysPerPeriod: "0", carryLimitDays: "3.5" },
    );
  });

test("lost save keeps its original command through verification and a later version conflict", async ({
  page,
}) => {
  const api = await installLeavePolicyEditorApi(page);
  await openEditor(page);
  api.dropNext();
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed");
  await expect(page.getByLabel("Name", { exact: true })).toHaveAttribute("readonly", "");
  const reads = api.reads.length;
  api.identity.expireMfa();
  api.rejectNext("mfa_required");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Annual policy review");
  expect(api.writes).toHaveLength(2);
  api.rejectNext("stale_version");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("button", { name: "Retry save", exact: true })).toBeEnabled();
  await expect(page.getByRole("button", { name: "Load current policy", exact: true })).toHaveCount(
    0,
  );
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  expect(api.reads).toHaveLength(reads);
  expect(api.writes).toHaveLength(4);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
});

test("a definite policy conflict requires protected reload and a new reviewed version", async ({
  page,
}) => {
  const api = await installLeavePolicyEditorApi(page);
  await openEditor(page);
  api.advance(companyIds[0], leavePolicyId());
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Load current policy", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Load current policy", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Other writer policy");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed the current policy");
  await page.getByRole("button", { name: "Save policy", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Policy saved.");
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([23, 24]);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
});

test("pending editor read is cancelled when the selected company changes", async ({ page }) => {
  const api = await installLeavePolicyEditorApi(page);
  const held = api.hold("details");
  try {
    await page.goto(editor);
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Leave policies", exact: true })).toContainText(
      "South leave 01",
    );
    held.release();
    await expect(page.getByRole("main")).not.toContainText("North leave");
  } finally {
    held.release();
  }
});

test("confirmed departure during a save ignores its late receipt in the next company", async ({
  page,
}) => {
  const api = await installLeavePolicyEditorApi(page);
  await openEditor(page);
  const held = api.holdSave();
  try {
    await page.getByRole("button", { name: "Save policy", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page.getByRole("table", { name: "Leave policies", exact: true })).toContainText(
      "South leave 01",
    );
    held.release();
    await expect(page.getByRole("main")).not.toContainText("Policy saved.");
    expect(api.writes).toHaveLength(1);
  } finally {
    held.release();
  }
});

test("editor keeps its draft through locale and theme changes in a narrow layout", async ({
  page,
}) => {
  const api = await installLeavePolicyEditorApi(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await openEditor(page);
  await page.getByLabel("Name", { exact: true }).fill("Reviewed annual allowance");
  const reads = api.reads.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByLabel("Nama", { exact: true })).toHaveValue("Reviewed annual allowance");
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Annual policy review");
  await expect(page.getByLabel("Nama", { exact: true })).toHaveCSS("color", "rgb(255, 255, 255)");
  expect(api.reads).toHaveLength(reads);
  expect(api.writes).toHaveLength(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/dashboard-leave-policy-editor-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
});
