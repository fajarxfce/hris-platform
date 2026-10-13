import { expect, type Page, test } from "@playwright/test";
import { installExpenseReconciliationApi } from "./expense-reconciliation-api";
import { expenseIdentifier } from "./fixtures/expense-claims";
import { companyIds } from "./identity-api";

const query = `company=${companyIds[0]}&from=2026-10-01&until=2026-10-31`,
  path = `/expenses/payments/${expenseIdentifier(80001)}`,
  editor = `${path}/reconcile?${query}`;
async function enterSuccess(page: Page, index = 1, reference = `BANK-${index}`) {
  await page
    .getByRole("button", {
      name: `Edit result: Finance Employee ${index} · EMP-00${index}`,
      exact: true,
    })
    .click();
  const panel = page.getByRole("dialog", { name: "Bank result", exact: true });
  await panel.getByRole("combobox", { name: "Bank result", exact: true }).selectOption("SUCCEEDED");
  await panel.getByLabel("Bank transaction reference", { exact: true }).fill(reference);
  await panel
    .getByLabel("Bank confirmation time (Asia/Jakarta)", { exact: true })
    .fill("2026-10-13T13:01");
  await panel.getByLabel("Result note", { exact: true }).fill("Confirmed on bank statement");
  await panel.getByRole("button", { name: "Add to reconciliation", exact: true }).click();
  await expect(panel).toHaveCount(0);
}
test("reconciles multiple confirmed results atomically and leaves unentered payments pending", async ({
  page,
}) => {
  const api = await installExpenseReconciliationApi(page);
  await page.goto(`${path}?${query}`);
  await page.getByRole("button", { name: "Reconcile payments", exact: true }).click();
  await expect(page).toHaveURL(editor);
  await expect(page.getByRole("button", { name: "Submit results", exact: true })).toBeDisabled();
  await enterSuccess(page);
  await page
    .getByRole("button", { name: "Edit result: Finance Employee 2 · EMP-002", exact: true })
    .click();
  const panel = page.getByRole("dialog", { name: "Bank result", exact: true });
  await expect(panel).toContainText("001234567801");
  await expect(page.getByRole("button", { name: "Leave page", exact: true })).toHaveCount(0);
  await panel.getByRole("combobox", { name: "Bank result", exact: true }).selectOption("FAILED");
  await panel
    .getByLabel("Bank confirmation time (Asia/Jakarta)", { exact: true })
    .fill("2026-10-13T13:01");
  await panel.getByLabel("Result note", { exact: true }).fill("Bank confirmed no transfer");
  await panel.getByRole("button", { name: "Add to reconciliation", exact: true }).click();
  await expect(panel.getByRole("alert")).toBeVisible();
  expect(api.writes).toHaveLength(0);
  await panel.getByRole("checkbox").check();
  await panel.getByRole("button", { name: "Add to reconciliation", exact: true }).click();
  await expect(panel).toHaveCount(0);
  await page.getByLabel("Reconciliation note", { exact: true }).fill("Two bank outcomes confirmed");
  const held = api.holdWrite();
  try {
    await page.getByRole("button", { name: "Submit results", exact: true }).click();
    await held.started;
    await expect(page.getByRole("button", { name: "Submit results", exact: true })).toBeDisabled();
    await expect(
      page.getByRole("button", { name: "Edit result: Finance Employee 3 · EMP-003", exact: true }),
    ).toBeDisabled();
    expect(api.writes).toHaveLength(1);
    expect(api.writes[0]?.body.results).toHaveLength(2);
    held.release();
    await expect(page.getByRole("status")).toContainText("Payment results recorded.");
    await page.getByRole("link", { name: "Batch details", exact: true }).last().click();
    await expect(page.getByRole("region", { name: "Pending", exact: true })).toContainText("1");
    await page.getByRole("tab", { name: "Reconciliation", exact: true }).click();
    const table = page.getByRole("table", { name: "Reconciliation", exact: true });
    await expect(table.getByRole("row")).toHaveCount(3);
    await expect(table).toContainText("BANK-1");
    await expect(table).toContainText("Failed");
    expect(api.commits).toBe(1);
    expect(api.records.get(companyIds[0])?.[0]?.results).toHaveLength(1);
    expect(api.records.get(companyIds[0])?.[0]?.history.items).toHaveLength(3);
    expect(api.identity.unhandled).toEqual([]);
  } finally {
    held.release();
  }
});
test("edits and removes staged results without navigation prompts or refetch", async ({ page }) => {
  const api = await installExpenseReconciliationApi(page);
  await page.goto(editor);
  await enterSuccess(page);
  const reads = api.reads.length;
  await page
    .getByRole("button", { name: "Edit result: Finance Employee 1 · EMP-001", exact: true })
    .click();
  const panel = page.getByRole("dialog", { name: "Bank result", exact: true });
  await expect(panel.getByLabel("Bank transaction reference", { exact: true })).toHaveValue(
    "BANK-1",
  );
  await panel.getByLabel("Bank transaction reference", { exact: true }).fill("CORRECTED");
  await panel.getByRole("button", { name: "Update result", exact: true }).click();
  await expect(panel).toHaveCount(0);
  await page
    .getByRole("button", { name: "Edit result: Finance Employee 1 · EMP-001", exact: true })
    .click();
  await expect(panel.getByLabel("Bank transaction reference", { exact: true })).toHaveValue(
    "CORRECTED",
  );
  await panel.getByRole("button", { name: "Cancel", exact: true }).click();
  await expect(panel).toHaveCount(0);
  await page
    .getByRole("button", { name: "Edit result: Finance Employee 1 · EMP-001", exact: true })
    .click();
  await panel.getByRole("button", { name: "Remove result", exact: true }).click();
  await expect(panel).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Submit results", exact: true })).toBeDisabled();
  expect(api.reads).toHaveLength(reads);
  expect(api.writes).toHaveLength(0);
});
test("the page owns departure protection for an unsaved result panel", async ({ page }) => {
  const api = await installExpenseReconciliationApi(page);
  await page.goto(`${path}?${query}`);
  await page.getByRole("button", { name: "Reconcile payments", exact: true }).click();
  await page
    .getByRole("button", { name: "Edit result: Finance Employee 1 · EMP-001", exact: true })
    .click();
  const panel = page.getByRole("dialog", { name: "Bank result", exact: true });
  await panel.getByRole("combobox", { name: "Bank result", exact: true }).selectOption("FAILED");
  await page.goBack();
  await page.getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(panel.getByRole("combobox", { name: "Bank result", exact: true })).toHaveValue(
    "FAILED",
  );
  await panel.getByRole("button", { name: "Close", exact: true }).click();
  await expect(panel).toHaveCount(0);
  await page.goBack();
  await expect(page).toHaveURL(`${path}?${query}`);
  await expect(page.getByRole("button", { name: "Leave page", exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});
test("lost result response retains the complete original reconciliation across MFA and later denial", async ({
  page,
}) => {
  const api = await installExpenseReconciliationApi(page);
  await page.goto(editor);
  await enterSuccess(page);
  await enterSuccess(page, 2);
  await page.getByLabel("Reconciliation note", { exact: true }).fill("Original reconciliation");
  const reads = api.reads.length;
  api.loseNext();
  await page.getByRole("button", { name: "Submit results", exact: true }).click();
  const retry = page.getByRole("button", { name: "Retry original reconciliation", exact: true });
  await expect(retry).toBeVisible();
  api.identity.expireMfa();
  api.rejectWrite("mfa_required", 403);
  await retry.click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByLabel("Reconciliation note", { exact: true })).toHaveCount(0);
  api.rejectWrite("expense_payment_item_not_pending");
  await retry.click();
  await expect(retry).toBeVisible();
  await retry.click();
  await expect(page.getByRole("status")).toContainText("Payment results recorded.");
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(4);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
  expect(api.reads).toHaveLength(reads);
});
test("definite conflicts require explicit reload and new result entry", async ({ page }) => {
  const api = await installExpenseReconciliationApi(page);
  await page.goto(editor);
  await enterSuccess(page);
  await page.getByLabel("Reconciliation note", { exact: true }).fill("Old review");
  api.rejectWrite("stale_version");
  await page.getByRole("button", { name: "Submit results", exact: true }).click();
  await expect(page.getByRole("button", { name: "Submit results", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  await expect(page.getByLabel("Reconciliation note", { exact: true })).toHaveValue("Old review");
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("button", { name: "Submit results", exact: true })).toBeDisabled();
  await enterSuccess(page, 2, "NEW-REVIEW");
  await page.getByLabel("Reconciliation note", { exact: true }).fill("Fresh review");
  await page.getByRole("button", { name: "Submit results", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Payment results recorded.");
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
  expect(api.writes[1]?.body.results[0]?.transactionReference).toBe("NEW-REVIEW");
  expect(api.commits).toBe(1);
});
test("finance rejection removes payment evidence and editing controls", async ({ page }) => {
  const api = await installExpenseReconciliationApi(page);
  await page.goto(editor);
  await enterSuccess(page);
  await page.getByLabel("Reconciliation note", { exact: true }).fill("Checked");
  api.rejectWrite("access_denied", 403);
  await page.getByRole("button", { name: "Submit results", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Submit results", exact: true })).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("PRIVATE BANK DATA");
  await expect(page.getByRole("main")).not.toContainText("Finance Employee");
  expect(api.commits).toBe(0);
});
test("company replacement cancels a pending reconciliation review", async ({ page }) => {
  const api = await installExpenseReconciliationApi(page),
    held = api.holdNext("review");
  try {
    await page.goto(editor);
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Expense payments", exact: true })).toContainText(
      "South reimbursements 01",
    );
    held.release();
    await expect(page.getByLabel("Reconciliation note", { exact: true })).toHaveCount(0);
    expect(api.writes).toHaveLength(0);
  } finally {
    held.release();
  }
});
test("server-disabled reconciliation cannot be bypassed using its route", async ({ page }) => {
  const api = await installExpenseReconciliationApi(page),
    record = api.records.get(companyIds[0])?.[0];
  if (!record) throw new Error("Released batch required");
  record.availableActions = ["EXPORT"];
  await page.goto(`${path}?${query}`);
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Payment batch", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Reconcile payments", exact: true })).toHaveCount(
    0,
  );
  await page.goto(editor);
  await expect(page.getByRole("alert")).toContainText("Reconciliation is unavailable");
  await expect(page.getByRole("table")).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});
test("reconciliation layout preserves staged input across theme and language changes", async ({
  page,
}) => {
  const api = await installExpenseReconciliationApi(page);
  await page.goto(editor);
  await enterSuccess(page);
  await page.getByLabel("Reconciliation note", { exact: true }).fill("Verified bank statement");
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-expense-reconciliation.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 760, height: 900 });
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Catatan rekonsiliasi", { exact: true })).toHaveValue(
    "Verified bank statement",
  );
  await page
    .getByRole("button", { name: "Isi hasil: Finance Employee 1 · EMP-001", exact: true })
    .click();
  const panel = page.getByRole("dialog", { name: "Hasil bank", exact: true });
  await expect(panel.getByLabel("Referensi transaksi bank", { exact: true })).toHaveValue("BANK-1");
  expect(api.reads).toHaveLength(1);
  await page.screenshot({
    path: "../../.work/dashboard-expense-reconciliation-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});
