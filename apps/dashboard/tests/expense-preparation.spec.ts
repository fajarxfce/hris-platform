import { expect, type Page, test } from "@playwright/test";
import { installExpensePreparationApi } from "./expense-preparation-api";
import { companyIds } from "./identity-api";

const query = `company=${companyIds[0]}&from=2026-10-01&until=2026-10-31`,
  directory = `/expenses/payments?${query}`,
  editor = `/expenses/payments/new?${query}`;
async function bankDetails(page: Page, index = 1, account = "001234567890") {
  await page
    .getByRole("button", {
      name: `Edit bank details: Travel claim ${index} · Payable Employee ${index}`,
      exact: true,
    })
    .click();
  const panel = page.getByRole("dialog", { name: "Bank details", exact: true });
  await panel.getByLabel("Bank code", { exact: true }).fill("014");
  await panel.getByLabel("Account number", { exact: true }).fill(account);
  await panel.getByLabel("Account name", { exact: true }).fill(`Payable Employee ${index}`);
  await panel.getByRole("button", { name: "Save bank details", exact: true }).click();
  await expect(panel).toHaveCount(0);
}
async function selectClaims(page: Page, indices = [1, 2]) {
  await page.getByRole("button", { name: "Add claims", exact: true }).click();
  const picker = page.getByRole("dialog", { name: "Select approved claims", exact: true });
  for (const index of indices)
    await picker
      .getByRole("button", {
        name: `Add claim: Travel claim ${index} · Payable Employee ${index}`,
        exact: true,
      })
      .click();
  await picker.getByRole("button", { name: "Done", exact: true }).click();
  await expect(picker).toHaveCount(0);
}
async function fill(page: Page, indices = [1, 2]) {
  await page.getByLabel("Batch title", { exact: true }).fill("October reimbursements");
  await page.getByLabel("Preparation note", { exact: true }).fill("Bank details verified");
  await selectClaims(page, indices);
  for (const index of indices) await bankDetails(page, index);
}
test("prepares claims across discovery pages with explicit bank review and one creation", async ({
  page,
}) => {
  const api = await installExpensePreparationApi(page);
  await page.goto(directory);
  await page.getByRole("button", { name: "Prepare payment batch", exact: true }).click();
  await expect(page).toHaveURL(editor);
  await page.getByLabel("Batch title", { exact: true }).fill("October reimbursements");
  await page.getByLabel("Preparation note", { exact: true }).fill("Bank details verified");
  await expect(
    page.getByRole("button", { name: "Review instructions", exact: true }),
  ).toBeDisabled();
  await page.getByRole("button", { name: "Add claims", exact: true }).click();
  const picker = page.getByRole("dialog", { name: "Select approved claims", exact: true });
  await expect(
    picker.getByRole("button", {
      name: "Add claim: Travel claim 5 · Payable Employee 5",
      exact: true,
    }),
  ).toBeDisabled();
  await picker
    .getByRole("button", { name: "Add claim: Travel claim 1 · Payable Employee 1", exact: true })
    .click();
  await picker
    .getByRole("heading", { name: "Select approved claims", exact: true })
    .first()
    .scrollIntoViewIfNeeded();
  await page.screenshot({
    path: "../../.work/dashboard-expense-preparation-picker.png",
    fullPage: true,
    animations: "disabled",
  });
  await expect(
    picker.getByRole("button", {
      name: "Add claim: Travel claim 1 · Payable Employee 1",
      exact: true,
    }),
  ).toBeDisabled();
  expect(api.payableReads).toHaveLength(1);
  await picker.getByRole("button", { name: "Next page", exact: true }).click();
  await picker
    .getByRole("button", { name: "Add claim: Travel claim 21 · Payable Employee 21", exact: true })
    .click();
  await picker.getByRole("button", { name: "Done", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Review instructions", exact: true }),
  ).toBeDisabled();
  await bankDetails(page, 1);
  await bankDetails(page, 21, "000987654321");
  expect(api.writes).toHaveLength(0);
  await page.getByRole("button", { name: "Review instructions", exact: true }).click();
  const instructions = page.getByRole("table", { name: "Bank instructions", exact: true });
  await expect(instructions).toContainText("001234567890");
  await expect(instructions).toContainText("000987654321");
  const held = api.holdWrite();
  try {
    await page.getByRole("button", { name: "Create batch", exact: true }).click();
    await held.started;
    await expect(page.getByRole("button", { name: "Create batch", exact: true })).toHaveCount(0);
    expect(api.writes).toHaveLength(1);
    expect(api.writes[0]?.body.items).toHaveLength(2);
    held.release();
    await expect(page.getByRole("status")).toContainText("Payment batch prepared.");
    await page.getByRole("link", { name: "Batch details", exact: true }).click();
    await expect(page.getByRole("region", { name: "Payment batch", exact: true })).toContainText(
      "425,001.00",
    );
    expect(api.commits).toBe(1);
    expect(api.payableReads).toHaveLength(2);
    expect(api.identity.unhandled).toEqual([]);
  } finally {
    held.release();
  }
});
test("bank editing validates input, keeps leading zeroes and removes a selection locally", async ({
  page,
}) => {
  const api = await installExpensePreparationApi(page);
  await page.goto(editor);
  await fill(page);
  await page
    .getByRole("button", {
      name: "Edit bank details: Travel claim 1 · Payable Employee 1",
      exact: true,
    })
    .click();
  const panel = page.getByRole("dialog", { name: "Bank details", exact: true });
  await expect(panel.getByLabel("Account number", { exact: true })).toHaveValue("001234567890");
  await panel.getByLabel("Account number", { exact: true }).fill("12x");
  await panel.getByRole("button", { name: "Save bank details", exact: true }).click();
  await expect(panel.getByRole("alert")).toContainText("Enter a valid bank code");
  await panel.getByLabel("Account number", { exact: true }).fill("000777777");
  await panel.getByRole("button", { name: "Save bank details", exact: true }).click();
  await page.getByRole("button", { name: "Review instructions", exact: true }).click();
  await expect(page.getByRole("table", { name: "Bank instructions", exact: true })).toContainText(
    "000777777",
  );
  await page.getByRole("button", { name: "Edit instructions", exact: true }).click();
  await page
    .getByRole("button", {
      name: "Edit bank details: Travel claim 1 · Payable Employee 1",
      exact: true,
    })
    .click();
  await panel.getByRole("button", { name: "Remove claim", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Selected payments", exact: true }),
  ).not.toContainText("Travel claim 1");
  expect(api.payableReads).toHaveLength(1);
  expect(api.writes).toHaveLength(0);
});
test("lost preparation response preserves the original batch and all item IDs across MFA and later conflict", async ({
  page,
}) => {
  const api = await installExpensePreparationApi(page);
  await page.goto(editor);
  await fill(page);
  await page.getByRole("button", { name: "Review instructions", exact: true }).click();
  api.loseNext();
  await page.getByRole("button", { name: "Create batch", exact: true }).click();
  const retry = page.getByRole("button", { name: "Retry original preparation", exact: true });
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
  await expect(page.getByRole("table")).toHaveCount(0);
  api.rejectWrite("expense_claim_payment_reserved");
  await retry.click();
  await expect(retry).toBeVisible();
  await retry.click();
  await expect(page.getByRole("status")).toContainText("Payment batch prepared.");
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(4);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
  expect(api.payableReads).toHaveLength(1);
});
test("known reservation rejection permits correction only after another explicit review", async ({
  page,
}) => {
  const api = await installExpensePreparationApi(page);
  await page.goto(editor);
  await fill(page);
  await page.getByRole("button", { name: "Review instructions", exact: true }).click();
  api.rejectWrite("expense_claim_payment_reserved");
  await page.getByRole("button", { name: "Create batch", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("reserved by another batch");
  await expect(page.getByRole("button", { name: "Create batch", exact: true })).toHaveCount(0);
  await page
    .getByRole("button", {
      name: "Edit bank details: Travel claim 1 · Payable Employee 1",
      exact: true,
    })
    .click();
  await page
    .getByRole("dialog", { name: "Bank details", exact: true })
    .getByRole("button", { name: "Remove claim", exact: true })
    .click();
  await page.getByRole("button", { name: "Review instructions", exact: true }).click();
  await page.getByRole("button", { name: "Create batch", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Payment batch prepared.");
  expect(api.writes).toHaveLength(2);
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
  expect(api.writes[1]?.id).not.toBe(api.writes[0]?.id);
  expect(api.writes[1]?.body.items).toHaveLength(1);
});
test("navigation protection belongs to the page while a bank panel is being edited", async ({
  page,
}) => {
  const api = await installExpensePreparationApi(page);
  await page.goto(directory);
  await page.getByRole("button", { name: "Prepare payment batch", exact: true }).click();
  await selectClaims(page, [1]);
  await page
    .getByRole("button", {
      name: "Edit bank details: Travel claim 1 · Payable Employee 1",
      exact: true,
    })
    .click();
  const panel = page.getByRole("dialog", { name: "Bank details", exact: true });
  await panel.getByLabel("Account number", { exact: true }).fill("001234567890");
  await page.goBack();
  await page.getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(panel.getByLabel("Account number", { exact: true })).toHaveValue("001234567890");
  await panel.getByRole("button", { name: "Close", exact: true }).click();
  await page.goBack();
  await page.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page).toHaveURL(directory);
  expect(api.writes).toHaveLength(0);
});
test("a denied payable refresh withdraws selected bank data and the picker", async ({ page }) => {
  const api = await installExpensePreparationApi(page);
  await page.goto(editor);
  await fill(page);
  api.rejectRead("access_denied");
  await page.getByRole("button", { name: "Add claims", exact: true }).click();
  await expect(
    page.getByRole("dialog", { name: "Select approved claims", exact: true }),
  ).toHaveCount(0);
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("Payable Employee");
  await expect(page.getByRole("main")).not.toContainText("PRIVATE BANK DATA");
  expect(api.writes).toHaveLength(0);
});
test("a denied initial preparation hides bank details without presenting a new create action", async ({
  page,
}) => {
  const api = await installExpensePreparationApi(page);
  await page.goto(editor);
  await fill(page);
  await page.getByRole("button", { name: "Review instructions", exact: true }).click();
  api.rejectWrite("independent_expense_payment_required", 403);
  await page.getByRole("button", { name: "Create batch", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("independent finance operator");
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Create batch", exact: true })).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("001234567890");
  expect(api.commits).toBe(0);
});
test("company replacement cancels discovery and hides its late results", async ({ page }) => {
  const api = await installExpensePreparationApi(page),
    held = api.holdRead();
  try {
    await page.goto(editor);
    await page.getByRole("button", { name: "Add claims", exact: true }).click();
    await held.started;
    await page
      .getByRole("dialog", { name: "Select approved claims", exact: true })
      .getByRole("button", { name: "Close", exact: true })
      .click();
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Expense payments", exact: true })).toContainText(
      "South reimbursements 01",
    );
    held.release();
    await expect(page.getByRole("main")).not.toContainText("Payable Employee");
    expect(api.writes).toHaveLength(0);
  } finally {
    held.release();
  }
});
test("preparation requires finance access before any payable acquisition", async ({ page }) => {
  const api = await installExpensePreparationApi(page, ["expenses.manage"]);
  await page.goto(editor);
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("button", { name: "Add claims", exact: true })).toHaveCount(0);
  expect(api.payableReads).toHaveLength(0);
  expect(api.writes).toHaveLength(0);
});
test("retains form and bank destinations through theme and language changes in wide and narrow layouts", async ({
  page,
}) => {
  const api = await installExpensePreparationApi(page);
  await page.goto(editor);
  await fill(page);
  await page.getByRole("button", { name: "Review instructions", exact: true }).click();
  await page
    .getByRole("heading", { name: "Review payment batch", exact: true })
    .scrollIntoViewIfNeeded();
  await page.screenshot({
    path: "../../.work/dashboard-expense-preparation.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Edit instructions", exact: true }).click();
  await page.setViewportSize({ width: 760, height: 900 });
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Judul batch", { exact: true })).toHaveValue(
    "October reimbursements",
  );
  await page
    .getByRole("button", {
      name: "Edit rekening: Travel claim 1 · Payable Employee 1",
      exact: true,
    })
    .click();
  const panel = page.getByRole("dialog", { name: "Detail rekening", exact: true });
  await expect(panel.getByLabel("Nomor rekening", { exact: true })).toHaveValue("001234567890");
  await page.screenshot({
    path: "../../.work/dashboard-expense-preparation-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(api.payableReads).toHaveLength(1);
  expect(api.writes).toHaveLength(0);
});
