import { expect, type Page, test } from "@playwright/test";
import { payrollIdentifier } from "./fixtures/payroll-payment-identifiers";
import { payrollRecoveryReview } from "./fixtures/payroll-payment-recoveries";
import { companyIds } from "./identity-api";
import { installPayrollPaymentRecoveriesApi } from "./payroll-payment-recoveries-api";

const assessment = payrollRecoveryReview().assessmentId,
  root = `/payroll/payslips/${assessment}/returns`,
  query = `company=${companyIds[0]}&batch=${payrollIdentifier(80001)}&payment=${payrollIdentifier(90100)}`,
  review = `${root}?${query}`,
  editor = `${root}/new?${query}`;
async function enterReturn(page: Page, reference = "RETURN-NEW", amount = "500.25") {
  await page.getByLabel("Returned amount (IDR)", { exact: true }).fill(amount);
  await page.getByLabel("Bank transaction reference", { exact: true }).fill(reference);
  await page
    .getByLabel("Bank confirmation time (Asia/Jakarta)", { exact: true })
    .fill("2026-10-14T13:30");
  await page.getByLabel("Return note", { exact: true }).fill("Confirmed incoming bank transfer");
}
test("finance discovers a return from its payment, records it once and reviews the retained evidence", async ({
  page,
}) => {
  const api = await installPayrollPaymentRecoveriesApi(page);
  await page.goto(
    `/payroll/payments/${payrollIdentifier(80001)}?company=${companyIds[0]}&view=results`,
  );
  await page
    .getByRole("button", {
      name: "View payment: Finance Employee 1 · EMP-001 · 2026-09",
      exact: true,
    })
    .click();
  await page
    .getByRole("dialog", { name: "Payment details", exact: true })
    .getByRole("link", { name: "View balance and returns", exact: true })
    .click();
  await expect(page).toHaveURL(review);
  await expect(page.getByRole("region", { name: "Payment balance", exact: true })).toContainText(
    "900.25",
  );
  await expect(
    page.getByRole("table", { name: "Return history (Asia/Jakarta)", exact: true }),
  ).toContainText("RETURN-ORIGINAL");
  await page.getByRole("button", { name: "Record return", exact: true }).click();
  await expect(page).toHaveURL(editor);
  await enterReturn(page);
  const held = api.holdRecovery("PUT");
  try {
    await page.getByRole("button", { name: "Confirm return", exact: true }).click();
    await held.entered;
    await expect(page.getByRole("button", { name: "Confirm return", exact: true })).toBeDisabled();
    await expect(page.getByRole("button", { name: "Reload review", exact: true })).toBeDisabled();
    expect(api.writes).toHaveLength(1);
  } finally {
    held.release();
  }
  await expect(page.getByRole("status")).toContainText("Return recorded.");
  expect(api.commits).toBe(1);
  expect(api.writes[0]?.body).toMatchObject({
    assessmentRevision: 1,
    paymentVersion: 5,
    amount: "500.25",
    occurredAt: "2026-10-14T06:30:00Z",
  });
  await page.getByRole("link", { name: "View balance and returns", exact: true }).click();
  await expect(page.getByRole("region", { name: "Payment balance", exact: true })).toContainText(
    "400.00",
  );
  await page.getByRole("button", { name: "View return: RETURN-NEW", exact: true }).click();
  const detail = page.getByRole("dialog", { name: "Return details", exact: true });
  await expect(detail).toContainText("Confirmed incoming bank transfer");
  await expect(detail).toContainText("500.25");
  await detail.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("link", { name: "Payment batch", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Payment details", exact: true })).toBeVisible();
});
test("a lost response retains the exact return through MFA, conflict and original receipt recovery", async ({
  page,
}) => {
  const api = await installPayrollPaymentRecoveriesApi(page);
  await page.goto(editor);
  await enterReturn(page);
  api.loseNext();
  await page.getByRole("button", { name: "Confirm return", exact: true }).click();
  const retry = page.getByRole("button", { name: "Retry original return", exact: true });
  await expect(retry).toBeVisible();
  api.identity.expireMfa();
  api.rejectRecovery("PUT", "mfa_required");
  await retry.click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Payment balance", exact: true })).toHaveCount(0);
  api.rejectRecovery("PUT", "payroll_payment_balance_changed", 409);
  await retry.click();
  await expect(retry).toBeVisible();
  await retry.click();
  await expect(page.getByRole("status")).toContainText("Return recorded.");
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(4);
  expect(api.recoveryReads).toHaveLength(1);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
  await expect(page.getByRole("main")).not.toContainText("PRIVATE FINANCE DATA");
});
test("a definite conflict requires explicit review and protects the existing draft", async ({
  page,
}) => {
  const api = await installPayrollPaymentRecoveriesApi(page);
  await page.goto(editor);
  await enterReturn(page);
  const current = api.reviews.get(companyIds[0]);
  if (!current) throw new Error("Review required");
  current.payments.version += 1;
  await page.getByRole("button", { name: "Confirm return", exact: true }).click();
  await expect(page.getByRole("button", { name: "Confirm return", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  await expect(page.getByLabel("Bank transaction reference", { exact: true })).toHaveValue(
    "RETURN-NEW",
  );
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Bank transaction reference", { exact: true })).toHaveValue("");
  await enterReturn(page, "RETURN-REFRESHED");
  await page.getByRole("button", { name: "Confirm return", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Return recorded.");
  expect(api.writes[1]?.body.paymentVersion).toBe(6);
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
  expect(api.writes[1]?.id).not.toBe(api.writes[0]?.id);
  expect(api.commits).toBe(1);
});
test("finance denial removes financial data and input without displaying raw diagnostics", async ({
  page,
}) => {
  const api = await installPayrollPaymentRecoveriesApi(page);
  await page.goto(editor);
  await enterReturn(page);
  api.rejectRecovery("PUT", "access_denied");
  await page.getByRole("button", { name: "Confirm return", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("region", { name: "Payment balance", exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Return note", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("Finance Employee 1");
  await expect(page.getByRole("main")).not.toContainText("PRIVATE FINANCE DATA");
  expect(api.commits).toBe(0);
});
test("a beneficiary can inspect retained returns but cannot bypass its blocker through the form route", async ({
  page,
}) => {
  const api = await installPayrollPaymentRecoveriesApi(page),
    current = api.reviews.get(companyIds[0]);
  if (!current) throw new Error("Review required");
  current.canConfirm = false;
  current.blockers = ["BENEFICIARY"];
  await page.goto(review);
  await expect(
    page.getByRole("table", { name: "Return history (Asia/Jakarta)", exact: true }),
  ).toContainText("RETURN-ORIGINAL");
  await expect(page.getByRole("button", { name: "Record return", exact: true })).toHaveCount(0);
  await page.goto(editor);
  await expect(page.getByRole("main")).toContainText(
    "Another authorized finance operator must record your return.",
  );
  await expect(page.getByRole("button", { name: "Confirm return", exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});
test("invalid decimals and a bank time below the exact review boundary do not reach HTTP", async ({
  page,
}) => {
  const api = await installPayrollPaymentRecoveriesApi(page);
  await page.goto(editor);
  await enterReturn(page, "CHECK", "900.26");
  await page.getByRole("button", { name: "Confirm return", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await page.getByLabel("Returned amount (IDR)", { exact: true }).fill("100.00");
  await page
    .getByLabel("Bank confirmation time (Asia/Jakarta)", { exact: true })
    .fill("2026-10-14T13:00");
  await page.getByRole("button", { name: "Confirm return", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.writes).toHaveLength(0);
});
test("company replacement cannot restore a pending return review from the previous company", async ({
  page,
}) => {
  const api = await installPayrollPaymentRecoveriesApi(page),
    held = api.holdRecovery("GET");
  try {
    await page.goto(review);
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Payroll payments", exact: true })).toContainText(
      "South payroll 01",
    );
    held.release();
    await expect(page.getByRole("region", { name: "Payment balance", exact: true })).toHaveCount(0);
    expect(api.writes).toHaveLength(0);
  } finally {
    held.release();
  }
});
test("payroll reading alone cannot acquire finance return evidence", async ({ page }) => {
  const api = await installPayrollPaymentRecoveriesApi(page, ["payroll.read"]);
  await page.goto(review);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.recoveryReads).toHaveLength(0);
  await expect(page.getByRole("table")).toHaveCount(0);
});
test("return review and entry remain usable in narrow dark Indonesian layouts without losing the draft", async ({
  page,
}) => {
  const api = await installPayrollPaymentRecoveriesApi(page);
  await page.goto(review);
  await expect(
    page.getByRole("table", { name: "Return history (Asia/Jakarta)", exact: true }),
  ).toBeVisible();
  await page.screenshot({
    path: "../../.work/dashboard-payroll-returns.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Record return", exact: true }).click();
  await enterReturn(page, "LAYOUT-RETURN");
  const reads = api.recoveryReads.length;
  await page.setViewportSize({ width: 760, height: 900 });
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Referensi transaksi bank", { exact: true })).toHaveValue(
    "LAYOUT-RETURN",
  );
  await expect(page.getByLabel("Nominal pengembalian (IDR)", { exact: true })).toHaveValue(
    "500.25",
  );
  expect(api.recoveryReads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-payroll-return-entry-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});
