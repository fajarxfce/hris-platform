import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import { installExpensePaymentsApi } from "./expense-payments-api";
import { expenseIdentifier } from "./fixtures/expense-claims";
import { expensePaymentCsv } from "./fixtures/expense-payments";
import { companyIds } from "./identity-api";

const query = `company=${companyIds[0]}&from=2026-10-01&until=2026-10-31`;
const directory = `/expenses/payments?${query}`;
const detail = `/expenses/payments/${expenseIdentifier(80001)}?${query}`;
const downloadable = `/expenses/payments/${expenseIdentifier(80002)}?${query}&view=instructions`;
test("finance directory filters, paging and return preserve explicit search", async ({ page }) => {
  const api = await installExpensePaymentsApi(page);
  await page.goto(directory);
  const table = page.getByRole("table", { name: "Expense payments", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(4);
  await page
    .getByRole("button", { name: "View batch: North reimbursements 21", exact: true })
    .click();
  await expect(page.getByRole("region", { name: "Payment batch", exact: true })).toContainText(
    "450,002.25",
  );
  const returningList = api.holdNext("list");
  try {
    await page.getByRole("link", { name: "Expense payments", exact: true }).last().click();
    await expect(page).toHaveURL(`${directory}&after=${expenseIdentifier(80020)}`);
    await returningList.entered;
    await expect(table).toHaveCount(0);
  } finally {
    returningList.release();
  }
  await expect(table.getByRole("row")).toHaveCount(4);
  const before = api.reads.length;
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("CANCELLED");
  expect(api.reads).toHaveLength(before);
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(
    page.getByText("No payment batches match these filters.", { exact: true }),
  ).toBeVisible();
  expect(api.reads.at(-1)?.url.searchParams.get("after")).toBeNull();
  await page.getByLabel("Created until", { exact: true }).fill("2028-10-01");
  const requests = api.reads.length;
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("366 days");
  expect(api.reads).toHaveLength(requests);
});
test("payment tabs share one review and show partial settlement with independent activity paging", async ({
  page,
}) => {
  const api = await installExpensePaymentsApi(page);
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Payment batch", exact: true })).toContainText(
    "3,750,018.75",
  );
  await expect(page.getByRole("region", { name: "Pending", exact: true })).toContainText("2");
  await page.getByRole("tab", { name: "Bank instructions", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Bank instructions", exact: true }).getByRole("row"),
  ).toHaveCount(26);
  await expect(page.getByRole("button", { name: "Download CSV", exact: true })).toHaveCount(0);
  await page
    .getByRole("button", { name: "View payment: Finance Employee 1 · EMP-001", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toContainText("001234567800");
  await expect(page.getByRole("dialog")).toContainText("Confirmed on bank statement");
  await page.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("tab", { name: "Reconciliation", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Reconciliation", exact: true }).getByRole("row"),
  ).toHaveCount(24);
  await page
    .getByRole("button", { name: "View payment: Finance Employee 2 · EMP-002", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toContainText("Bank confirmed no transfer");
  await page.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("tab", { name: "Activity", exact: true }).click();
  const history = page.getByRole("table", { name: "Activity", exact: true });
  await expect(history.getByRole("row")).toHaveCount(21);
  expect(api.reads).toHaveLength(1);
  await page.getByRole("button", { name: "Next entries", exact: true }).click();
  await expect(history.getByRole("row")).toHaveCount(6);
  expect(api.reads.at(-1)?.url.searchParams.get("historyAfter")).toBe("19");
  await page.getByRole("button", { name: "View activity: Version 24", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("Bank confirmation 23");
  await page.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("tab", { name: "Overview", exact: true }).click();
  expect(api.reads).toHaveLength(2);
});
test("CSV download keeps the exact bytes, filename and leading zero account", async ({ page }) => {
  const api = await installExpensePaymentsApi(page);
  await page.goto(downloadable);
  const started = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download CSV", exact: true }).click();
  const download = await started,
    path = await download.path();
  expect(download.suggestedFilename()).toBe(`expense-payments-${expenseIdentifier(80002)}.csv`);
  expect(path).not.toBeNull();
  if (path) expect(await readFile(path, "utf8")).toBe(expensePaymentCsv);
  await expect(page.getByText("CSV download started.", { exact: true })).toBeVisible();
  expect(api.reads.filter((row) => row.kind === "worksheet")).toHaveLength(1);
});
test("cancelling an in-flight worksheet allows a fresh download without a duplicate artifact", async ({
  page,
}) => {
  const api = await installExpensePaymentsApi(page),
    downloads: string[] = [];
  page.on("download", (download) => downloads.push(download.suggestedFilename()));
  await page.goto(downloadable);
  const held = api.holdNext("worksheet");
  try {
    await page.getByRole("button", { name: "Download CSV", exact: true }).click();
    await held.entered;
    await expect(page.getByRole("button", { name: "Download CSV", exact: true })).toBeDisabled();
    await page.getByRole("button", { name: "Cancel download", exact: true }).click();
    held.release();
    await expect(page.getByRole("button", { name: "Download CSV", exact: true })).toBeEnabled();
    const started = page.waitForEvent("download");
    await page.getByRole("button", { name: "Download CSV", exact: true }).click();
    await started;
    await expect(page.getByText("CSV download started.", { exact: true })).toBeVisible();
    expect(downloads).toHaveLength(1);
  } finally {
    held.release();
  }
});
for (const code of [
  "access_denied",
  "recent_authentication_required",
  "expense_payment_instructions_unavailable",
]) {
  test(`worksheet ${code} removes bank details and requires explicit review`, async ({ page }) => {
    const api = await installExpensePaymentsApi(page);
    await page.goto(downloadable);
    api.rejectNext("worksheet", code);
    await page.getByRole("button", { name: "Download CSV", exact: true }).click();
    await expect(page.getByRole("alert")).toBeVisible();
    await expect(page.getByRole("table")).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Download CSV", exact: true })).toHaveCount(0);
    await expect(page.getByText("PRIVATE BANK DATA", { exact: true })).toHaveCount(0);
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(page.getByRole("table", { name: "Bank instructions", exact: true })).toContainText(
      "Finance Employee 1",
    );
  });
}
test("a company change discards a pending review and its activity cursor", async ({ page }) => {
  const api = await installExpensePaymentsApi(page),
    held = api.holdNext("review");
  try {
    await page.goto(`${detail}&view=history&historyAfter=19`);
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Expense payments", exact: true })).toContainText(
      "South reimbursements 01",
    );
    held.release();
    expect(new URL(page.url()).searchParams.get("historyAfter")).toBeNull();
    await expect(page.getByText("North reimbursements 01", { exact: true })).toHaveCount(0);
  } finally {
    held.release();
  }
});
test("a cancelled company-owned worksheet never produces a late download", async ({ page }) => {
  const api = await installExpensePaymentsApi(page),
    downloads: string[] = [];
  page.on("download", (download) => downloads.push(download.suggestedFilename()));
  await page.goto(downloadable);
  const held = api.holdNext("worksheet");
  try {
    await page.getByRole("button", { name: "Download CSV", exact: true }).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Expense payments", exact: true })).toContainText(
      "South reimbursements 01",
    );
    held.release();
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(page.getByRole("table", { name: "Expense payments", exact: true })).toBeVisible();
    expect(downloads).toHaveLength(0);
  } finally {
    held.release();
  }
});
test("malformed payment data remains hidden until a successful explicit refresh", async ({
  page,
}) => {
  const api = await installExpensePaymentsApi(page);
  api.malformedReview();
  await page.goto(detail);
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("region", { name: "Payment batch", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("region", { name: "Payment batch", exact: true })).toContainText(
    "3,750,018.75",
  );
});
test("finance read layout keeps data across theme and language changes at a narrow width", async ({
  page,
}) => {
  const api = await installExpensePaymentsApi(page);
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Payment batch", exact: true })).toBeVisible();
  await page.screenshot({
    path: "../../.work/dashboard-expense-payment-overview.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 760, height: 900 });
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await expect(page.getByRole("button", { name: "Theme", exact: true })).toHaveText("Light");
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByRole("region", { name: "Batch pembayaran", exact: true })).toContainText(
    "3.750.018,75",
  );
  await page.getByRole("tab", { name: "Rekonsiliasi", exact: true }).click();
  expect(api.reads).toHaveLength(1);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-expense-payment-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});
test("expense administrators cannot acquire finance data through a direct route", async ({
  page,
}) => {
  const api = await installExpensePaymentsApi(page, ["expenses.manage"]);
  await page.goto(detail);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads).toHaveLength(0);
  await expect(page.getByRole("table")).toHaveCount(0);
});
