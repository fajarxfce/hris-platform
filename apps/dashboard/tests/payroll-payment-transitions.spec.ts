import { expect, test } from "@playwright/test";
import { payrollIdentifier } from "./fixtures/payroll-payment-identifiers";
import { companyIds } from "./identity-api";
import { installPayrollPaymentTransitionsApi } from "./payroll-payment-transitions-api";

const query = `company=${companyIds[0]}&from=2026-10-01&until=2026-10-31`,
  path = `/payroll/payments/${payrollIdentifier(80001)}`,
  detail = `${path}?${query}`;
test("release reviews complete bank instructions and prevents repeated confirmation", async ({
  page,
}) => {
  const api = await installPayrollPaymentTransitionsApi(page);
  await page.goto(detail);
  await page.getByRole("button", { name: "Release batch", exact: true }).click();
  await expect(page).toHaveURL(`${path}/release?${query}`);
  await expect(
    page.getByRole("table", { name: "Payment instructions", exact: true }),
  ).toContainText("001234567800");
  await page.getByRole("button", { name: "Release batch", exact: true }).click();
  await expect(
    page.getByText("Enter a reason of up to 1,000 characters.", { exact: true }),
  ).toBeVisible();
  expect(api.writes).toHaveLength(0);
  await page.getByLabel("Reason", { exact: true }).fill("Verified every bank destination");
  await page
    .getByRole("button", {
      name: "View payment: Finance Employee 1 · EMP-001 · 2026-09",
      exact: true,
    })
    .click();
  await expect(page.getByRole("dialog")).toContainText("001234567800");
  await expect(page.getByRole("button", { name: "Leave page", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Close", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue(
    "Verified every bank destination",
  );
  const held = api.holdWrite();
  try {
    await page.getByRole("button", { name: "Release batch", exact: true }).click();
    await held.started;
    await expect(page.getByRole("button", { name: "Release batch", exact: true })).toBeDisabled();
    await expect(page.getByRole("button", { name: "Reload review", exact: true })).toHaveCount(0);
    expect(api.writes).toHaveLength(1);
    held.release();
    await expect(page.getByRole("status")).toContainText("Payment batch updated.");
    await page.getByRole("link", { name: "Batch details", exact: true }).last().click();
    await expect(page.getByRole("region", { name: "Payment batch", exact: true })).toContainText(
      "Released",
    );
    await expect(page.getByRole("button", { name: "Release batch", exact: true })).toHaveCount(0);
    expect(api.commits).toBe(1);
  } finally {
    held.release();
  }
});
test("a prepared batch can be cancelled after reason review", async ({ page }) => {
  const api = await installPayrollPaymentTransitionsApi(page);
  await page.goto(detail);
  await page.getByRole("button", { name: "Cancel batch", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Replace the verified bank destination");
  await page.getByRole("button", { name: "Cancel batch", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Payment batch updated.");
  await page.getByRole("link", { name: "Batch details", exact: true }).last().click();
  await expect(page.getByRole("region", { name: "Payment batch", exact: true })).toContainText(
    "Cancelled",
  );
  await expect(page.getByRole("button", { name: "Cancel batch", exact: true })).toHaveCount(0);
  expect(api.writes[0]?.action).toBe("cancel");
});
test("lost release response retains the original command across MFA verification and a later denial", async ({
  page,
}) => {
  const api = await installPayrollPaymentTransitionsApi(page);
  await page.goto(`${path}/release?${query}`);
  await page.getByLabel("Reason", { exact: true }).fill("Original bank review");
  const reads = api.reads.length;
  api.loseNext();
  await page.getByRole("button", { name: "Release batch", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Retry original action", exact: true }),
  ).toBeVisible();
  api.identity.expireMfa();
  api.rejectWrite("mfa_required", 403);
  await page.getByRole("button", { name: "Retry original action", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
  api.rejectWrite("independent_payment_release_required", 403);
  await page.getByRole("button", { name: "Retry original action", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Retry original action", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Retry original action", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Payment batch updated.");
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(4);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
  expect(api.reads).toHaveLength(reads);
  expect(api.identity.unhandled).toEqual([]);
});
test("a definite conflict protects edited input and requires an explicit new review", async ({
  page,
}) => {
  const api = await installPayrollPaymentTransitionsApi(page);
  await page.goto(`${path}/cancel?${query}`);
  await page.getByLabel("Reason", { exact: true }).fill("Old review");
  api.rejectWrite("stale_version");
  await page.getByRole("button", { name: "Cancel batch", exact: true }).click();
  await expect(page.getByRole("button", { name: "Cancel batch", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Old review");
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Reason", { exact: true }).fill("Updated review");
  await page.getByRole("button", { name: "Cancel batch", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Payment batch updated.");
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
  expect(api.commits).toBe(1);
});
test("a definite finance rejection removes account data and confirmation controls", async ({
  page,
}) => {
  const api = await installPayrollPaymentTransitionsApi(page);
  await page.goto(`${path}/release?${query}`);
  await page.getByLabel("Reason", { exact: true }).fill("Bank review");
  api.rejectWrite("access_denied", 403);
  await page.getByRole("button", { name: "Release batch", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Release batch", exact: true })).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("001234567800");
  await expect(page.getByRole("main")).not.toContainText("PRIVATE BANK DATA");
  expect(api.commits).toBe(0);
});
test("company replacement drops a pending transition review", async ({ page }) => {
  const api = await installPayrollPaymentTransitionsApi(page),
    held = api.holdNext("review");
  try {
    await page.goto(`${path}/release?${query}`);
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Payroll payments", exact: true })).toContainText(
      "South payroll 01",
    );
    held.release();
    await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
    expect(api.writes).toHaveLength(0);
  } finally {
    held.release();
  }
});
test("restricted release availability cannot be bypassed through a direct route", async ({
  page,
}) => {
  const api = await installPayrollPaymentTransitionsApi(page),
    record = api.records.get(companyIds[0])?.[0];
  if (!record) throw new Error("Prepared record required");
  record.availableActions = ["CANCEL"];
  await page.goto(detail);
  await expect(page.getByRole("button", { name: "Cancel batch", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Release batch", exact: true })).toHaveCount(0);
  await page.goto(`${path}/release?${query}`);
  await expect(page.getByRole("alert")).toContainText("This action is unavailable");
  await expect(page.getByRole("table")).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});
test("narrow dark release review retains its reason and avoids refetch on locale changes", async ({
  page,
}) => {
  const api = await installPayrollPaymentTransitionsApi(page);
  await page.goto(`${path}/release?${query}`);
  await page.getByLabel("Reason", { exact: true }).fill("Verified bank instructions");
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-payroll-payment-release.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 760, height: 900 });
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await expect(page.getByRole("button", { name: "Theme", exact: true })).toHaveText("Light");
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue(
    "Verified bank instructions",
  );
  expect(api.reads).toHaveLength(1);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-payroll-payment-release-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});
