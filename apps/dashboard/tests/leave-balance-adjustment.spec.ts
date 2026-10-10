import { expect, type Page, test } from "@playwright/test";
import { leaveEmployeeId } from "./fixtures/leave";
import { balanceEntryId } from "./fixtures/leave-balances";
import { leavePolicyId } from "./fixtures/leave-policies";
import { companyIds } from "./identity-api";
import { installLeaveBalanceAdjustmentApi } from "./leave-balance-adjustment-api";

const path = `/leave/employees/${leaveEmployeeId()}/balances`;
const query = `company=${companyIds[0]}&year=2026`;
const editor = `${path}/${leavePolicyId()}/adjust?${query}`;
async function openEditor(page: Page) {
  await page.goto(editor);
  await expect(page.getByRole("region", { name: "Current balance", exact: true })).toContainText(
    "North leave 01",
  );
  await page.getByLabel("Change in days", { exact: true }).fill("1.5");
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed balance correction");
}
async function verify(page: Page) {
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog).toHaveCount(0);
}

test("initial funding starts in an empty balance directory and records one immutable movement", async ({
  page,
}) => {
  const api = await installLeaveBalanceAdjustmentApi(page);
  await page.goto(`${path}?company=${companyIds[0]}&year=2027`);
  await expect(page.getByRole("status")).toContainText("No balance accounts");
  await page.getByRole("link", { name: "Adjust leave balance", exact: true }).click();
  await page.getByRole("button", { name: "Review balance: North leave 01", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current balance", exact: true })).toContainText(
    "No movements recorded",
  );
  await page.getByLabel("Change in days", { exact: true }).fill("+12.50");
  await page.getByLabel("Reason", { exact: true }).fill(" Opening balance ");
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Adjustment recorded.");
  expect(api.writes).toHaveLength(1);
  expect(api.writes[0]?.body).toEqual({
    days: "12.5",
    reason: "Opening balance",
    expectedVersion: 0,
  });
  expect(api.record(2027).entries.items).toHaveLength(1);
  await page.getByRole("link", { name: "View current balance", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current balance", exact: true })).toContainText(
    "12.5",
  );
  await page.getByRole("button", { name: /^View movement: Adjustment/ }).click();
  await expect(page.getByRole("region", { name: "Selected movement", exact: true })).toContainText(
    "Opening balance",
  );
});

test("the type picker retains its bounded page and balance-year context", async ({ page }) => {
  const api = await installLeaveBalanceAdjustmentApi(page);
  await page.goto(`${path}/adjust?${query}&directoryAfter=LEAVE000`);
  await expect(
    page.getByRole("table", { name: "Leave types", exact: true }).getByRole("row"),
  ).toHaveCount(21);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await page.getByRole("button", { name: "Review balance: North leave 21", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current balance", exact: true })).toContainText(
    "North leave 21",
  );
  await page.getByRole("link", { name: "Back to leave types", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Leave types", exact: true }).getByRole("row"),
  ).toHaveCount(4);
  expect(new URL(page.url()).searchParams.get("after")).toBe("LEAVE020");
  await page.getByRole("link", { name: "Back to balances", exact: true }).click();
  await expect(page).toHaveURL(`${path}?${query}&after=LEAVE000`);
  expect(api.reads.every((url) => url.searchParams.get("limit") === "20")).toBe(true);
  expect(api.writes).toHaveLength(0);
});

test("negative half-day corrections review current balances even when opened from older movements", async ({
  page,
}) => {
  const api = await installLeaveBalanceAdjustmentApi(page);
  await page.goto(`${path}/${leavePolicyId()}?${query}&after=${balanceEntryId(20)}`);
  await page.getByRole("link", { name: "Adjust leave balance", exact: true }).click();
  await page.getByLabel("Change in days", { exact: true }).fill("-1.50");
  await page.getByLabel("Reason", { exact: true }).fill("Correction to initial allocation");
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Adjustment recorded.");
  expect(api.writes[0]?.body).toMatchObject({ days: "-1.5", expectedVersion: 14 });
  expect(api.balanceReads.at(-1)?.searchParams.has("after")).toBe(false);
  expect(api.record().balance.availableDays).toBe("7");
  await page.getByRole("link", { name: "View current balance", exact: true }).click();
  expect(new URL(page.url()).searchParams.has("after")).toBe(false);
});

test("invalid precision, zero adjustments, and missing reasons never start an HTTP command", async ({
  page,
}) => {
  const api = await installLeaveBalanceAdjustmentApi(page);
  await openEditor(page);
  for (const days of ["1.25", "0"]) {
    await page.getByLabel("Change in days", { exact: true }).fill(days);
    await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
    await expect(page.getByLabel("Change in days", { exact: true })).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(api.writes).toHaveLength(0);
  }
  await page.getByLabel("Change in days", { exact: true }).fill("0.5");
  await page.getByLabel("Reason", { exact: true }).fill("");
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveAttribute("aria-invalid", "true");
  expect(api.writes).toHaveLength(0);
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed correction");
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Adjustment recorded.");
});

test("a competing balance change requires an explicit new review while retaining the draft", async ({
  page,
}) => {
  const api = await installLeaveBalanceAdjustmentApi(page);
  await openEditor(page);
  api.advance();
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("The balance changed");
  await expect(page.getByRole("button", { name: "Record adjustment", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Retry adjustment", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue(
    "Reviewed balance correction",
  );
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Adjustment recorded.");
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([14, 15]);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
});

test("a lost receipt stays bound to the original command through MFA and a later conflict", async ({
  page,
}) => {
  const api = await installLeaveBalanceAdjustmentApi(page);
  await openEditor(page);
  api.dropNext();
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The outcome is not confirmed");
  const reads = api.balanceReads.length;
  api.identity.expireMfa();
  api.rejectNext("mfa_required");
  await page.getByRole("button", { name: "Retry adjustment", exact: true }).click();
  await verify(page);
  await expect(page.getByRole("region", { name: "Current balance", exact: true })).toHaveCount(0);
  api.rejectNext("stale_balance_version");
  await page.getByRole("button", { name: "Retry adjustment", exact: true }).click();
  await expect(page.getByRole("button", { name: "Retry adjustment", exact: true })).toBeEnabled();
  await expect(page.getByRole("button", { name: "Reload review", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Retry adjustment", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Adjustment recorded.");
  expect(api.balanceReads).toHaveLength(reads);
  expect(api.writes).toHaveLength(4);
  expect(api.receipts.size).toBe(1);
  expect(api.record().balance.version).toBe(15);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
});

test("a definite MFA rejection requires re-review but preserves the amount and reason", async ({
  page,
}) => {
  const api = await installLeaveBalanceAdjustmentApi(page);
  await openEditor(page);
  api.identity.expireMfa();
  api.rejectNext("mfa_required");
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  const reads = api.balanceReads.length;
  await verify(page);
  expect(api.balanceReads).toHaveLength(reads);
  expect(api.writes).toHaveLength(1);
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await expect(page.getByLabel("Change in days", { exact: true })).toHaveValue("1.5");
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue(
    "Reviewed balance correction",
  );
  await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Adjustment recorded.");
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
});

for (const mode of ["closed", "unavailable"] as const)
  test(`${mode} balance has no adjustment link and a direct form cannot be used`, async ({
    page,
  }) => {
    const api = await installLeaveBalanceAdjustmentApi(page);
    const year = mode === "closed" ? 2025 : 2026;
    if (mode === "unavailable") api.record().availableActions = [];
    await page.goto(`${path}/${leavePolicyId()}?company=${companyIds[0]}&year=${year}`);
    await expect(page.getByRole("region", { name: "Current balance", exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "Adjust leave balance", exact: true })).toHaveCount(
      0,
    );
    await page.goto(`${path}/${leavePolicyId()}/adjust?company=${companyIds[0]}&year=${year}`);
    await expect(page.getByRole("alert")).toContainText("This balance cannot be adjusted");
    await expect(page.getByLabel("Change in days", { exact: true })).toHaveCount(0);
    expect(api.writes).toHaveLength(0);
  });

test("read-only users cannot open a catalog or initiate an adjustment", async ({ page }) => {
  const api = await installLeaveBalanceAdjustmentApi(page, ["leave.read"]);
  await page.goto(`${path}?${query}`);
  await expect(page.getByRole("table", { name: "Leave balances", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Adjust leave balance", exact: true })).toHaveCount(
    0,
  );
  const reads = api.balanceReads.length;
  await page.goto(`${path}/adjust?${query}`);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads).toHaveLength(0);
  expect(api.balanceReads).toHaveLength(reads);
  await page.goto(editor);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.balanceReads).toHaveLength(reads);
  expect(api.writes).toHaveLength(0);
});

test("a pending review cannot restore the former employee after company selection changes", async ({
  page,
}) => {
  const api = await installLeaveBalanceAdjustmentApi(page);
  await page.goto(editor);
  await expect(page.getByRole("region", { name: "Current balance", exact: true })).toBeVisible();
  const held = api.holdBalance("read");
  try {
    await page.getByRole("button", { name: "Reload review", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page).toHaveURL("/");
    held.release();
    await expect(page.getByRole("main")).not.toContainText("North Employee");
    await expect(page.getByRole("main")).not.toContainText("North leave");
  } finally {
    held.release();
  }
});

test("confirmed departure during a pending adjustment ignores a late receipt in the next company", async ({
  page,
}) => {
  const api = await installLeaveBalanceAdjustmentApi(page);
  await openEditor(page);
  const held = api.holdBalance("write");
  try {
    await page.getByRole("button", { name: "Record adjustment", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page).toHaveURL("/");
    held.release();
    await expect(page.getByRole("main")).not.toContainText("Adjustment recorded");
    expect(api.writes).toHaveLength(1);
  } finally {
    held.release();
  }
});

test("draft protection, language and theme changes preserve the adjustment in a narrow layout", async ({
  page,
}) => {
  const api = await installLeaveBalanceAdjustmentApi(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await openEditor(page);
  const reads = api.balanceReads.length;
  await page.getByRole("link", { name: "Back to ledger", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByLabel("Perubahan hari", { exact: true })).toHaveValue("1.5");
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue(
    "Reviewed balance correction",
  );
  await expect(page.getByLabel("Perubahan hari", { exact: true })).toHaveCSS(
    "color",
    "rgb(255, 255, 255)",
  );
  expect(api.balanceReads).toHaveLength(reads);
  expect(api.writes).toHaveLength(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: "../../.work/dashboard-leave-adjustment-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
});
