import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import { installExpenseClaimsApi } from "./expense-claims-api";
import { expenseIdentifier, expenseReceiptBytes } from "./fixtures/expense-claims";
import { companyIds } from "./identity-api";

const query = `company=${companyIds[0]}&from=2026-10-01&until=2026-10-31`,
  directory = `/expenses/claims?${query}`,
  detail = `/expenses/claims/${expenseIdentifier(1)}?${query}`;
const submitted = `/expenses/submissions/${expenseIdentifier(1001)}?${query}`;
test("claim filters and paging keep the selected company and do not search on each input edit", async ({
  page,
}) => {
  const api = await installExpenseClaimsApi(page);
  await page.goto(directory);
  const table = page.getByRole("table", { name: "Expense claims", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  expect(
    await table.evaluate(
      (node) =>
        node.parentElement !== null &&
        node.parentElement.scrollWidth <= node.parentElement.clientWidth,
    ),
  ).toBe(true);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(4);
  await page
    .getByRole("button", { name: "View: North travel reimbursement 21", exact: true })
    .click();
  await expect(page.getByRole("region", { name: "Current claim", exact: true })).toContainText(
    "150,000.75",
  );
  const returningList = api.holdNext("list");
  try {
    await page.getByRole("link", { name: "Expense claims", exact: true }).last().click();
    await expect(page).toHaveURL(`${directory}&after=${expenseIdentifier(20)}`);
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
  await expect(page.getByText("No expense claims in this period.", { exact: true })).toBeVisible();
  expect(api.reads.at(-1)?.url.searchParams.get("after")).toBeNull();
  await page.getByLabel("Created until", { exact: true }).fill("2028-10-01");
  const requests = api.reads.length;
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("366 days");
  expect(api.reads).toHaveLength(requests);
});
test("claim tabs reuse one review and history has an independent descending cursor", async ({
  page,
}) => {
  const api = await installExpenseClaimsApi(page);
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Current claim", exact: true })).toBeVisible();
  await page.getByRole("tab", { name: "Submissions", exact: true }).click();
  await expect(page.getByRole("table", { name: "Submissions", exact: true })).toContainText(
    "150,000.75",
  );
  await page.getByRole("tab", { name: "Payments", exact: true }).click();
  await expect(
    page.getByText("No payment instructions have been prepared.", { exact: true }),
  ).toBeVisible();
  await page.getByRole("tab", { name: "History", exact: true }).click();
  const table = page.getByRole("table", { name: "History", exact: true });
  await expect(table.getByRole("row")).toHaveCount(21);
  expect(api.reads.filter((row) => row.kind === "review")).toHaveLength(1);
  await page.getByRole("button", { name: "View action: Version 23", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("Saved travel revision 23");
  await page.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("button", { name: "Older", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(5);
  expect(api.reads.at(-1)?.url.searchParams.get("historyAfter")).toBe("4");
  await page.getByRole("tab", { name: "Overview", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current claim", exact: true })).toContainText(
    "23",
  );
  expect(api.reads.filter((row) => row.kind === "review")).toHaveLength(2);
});
test("approval source opens submitted expense evidence and downloads its exact receipt", async ({
  page,
}) => {
  const api = await installExpenseClaimsApi(page);
  await page.goto(`/approvals/${expenseIdentifier(7001)}?company=${companyIds[0]}`);
  await page.getByRole("link", { name: "View source request", exact: true }).click();
  await expect(page).toHaveURL(
    `/expenses/submissions/${expenseIdentifier(1001)}?company=${companyIds[0]}`,
  );
  await expect(page.getByRole("region", { name: "Expense submission", exact: true })).toContainText(
    "Pending",
  );
  await expect(
    page.getByText(
      "Potentially duplicated receipts require review. Open each item to inspect its evidence.",
      { exact: true },
    ),
  ).toBeVisible();
  await page.getByRole("button", { name: "View item: Return train ticket", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("Business travel (TRAVEL)");
  await expect(page.getByRole("dialog")).toContainText("Sales (SALES)");
  const downloadStarted = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download: train-receipt.pdf", exact: true }).click();
  const download = await downloadStarted;
  expect(download.suggestedFilename()).toBe("train-receipt.pdf");
  const path = await download.path();
  expect(path).not.toBeNull();
  if (path) expect(await readFile(path)).toEqual(Buffer.from(expenseReceiptBytes));
  await expect(page.getByText("Download started.", { exact: true })).toBeVisible();
  expect(api.reads.filter((row) => row.kind === "receipt")).toHaveLength(1);
});
test("receipt cancellation, retry and authorization loss leave no stale evidence visible", async ({
  page,
}) => {
  const api = await installExpenseClaimsApi(page);
  await page.goto(submitted);
  await page.getByRole("button", { name: "View item: Return train ticket", exact: true }).click();
  const hold = api.holdNext("receipt");
  try {
    await page.getByRole("button", { name: "Download: train-receipt.pdf", exact: true }).click();
    await hold.entered;
    await page.getByRole("button", { name: "Cancel download", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Download: train-receipt.pdf", exact: true }),
    ).toBeEnabled();
    hold.release();
    api.rejectNext("receipt", "expense_receipt_not_found");
    await page.getByRole("button", { name: "Download: train-receipt.pdf", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText("This receipt is unavailable");
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await expect(page.getByText("Sample Employee · EMP-017", { exact: true })).toHaveCount(0);
    await expect(page.getByText("PRIVATE TECHNICAL MESSAGE", { exact: true })).toHaveCount(0);
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(
      page.getByRole("region", { name: "Expense submission", exact: true }),
    ).toContainText("Sample Employee");
  } finally {
    hold.release();
  }
});
test("company replacement discards a held claim and its history cursor", async ({ page }) => {
  const api = await installExpenseClaimsApi(page),
    hold = api.holdNext("review");
  try {
    await page.goto(`${detail}&historyAfter=4&view=history`);
    await hold.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Expense claims", exact: true })).toContainText(
      "South travel reimbursement 01",
    );
    hold.release();
    expect(new URL(page.url()).searchParams.get("historyAfter")).toBeNull();
    await expect(page.getByText("North travel reimbursement 01", { exact: true })).toHaveCount(0);
  } finally {
    hold.release();
  }
});
test("invalid review responses stay hidden and a fresh read can recover", async ({ page }) => {
  const api = await installExpenseClaimsApi(page);
  api.malformedReview();
  await page.goto(detail);
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("region", { name: "Current claim", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current claim", exact: true })).toContainText(
    "Sample Employee",
  );
});
test("dark narrow submission details keep their place across locale changes without refetching", async ({
  page,
}) => {
  await page.emulateMedia({ colorScheme: "dark" });
  await page.setViewportSize({ width: 760, height: 900 });
  const api = await installExpenseClaimsApi(page);
  await page.goto(submitted);
  await page.getByRole("button", { name: "View item: Return train ticket", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("train-receipt.pdf");
  await page.getByRole("button", { name: "Close", exact: true }).click();
  const requests = api.reads.length;
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await expect(page.getByRole("button", { name: "Theme", exact: true })).toHaveText("Light");
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByRole("region", { name: "Pengajuan biaya", exact: true })).toContainText(
    "150.000,75",
  );
  expect(api.reads).toHaveLength(requests);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.locator(".app-table-scroll").evaluateAll((elements) =>
    elements.forEach((element) => {
      element.scrollLeft = 0;
    }),
  );
  await page.screenshot({
    path: "../../.work/dashboard-expense-claims-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});
test("policy administrators cannot fetch claim data through a direct route", async ({ page }) => {
  const api = await installExpenseClaimsApi(page, ["expenses.policy.manage"]);
  await page.goto(detail);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.reads).toHaveLength(0);
  await expect(page.getByRole("link", { name: "Expense claims", exact: true }).first()).toHaveCount(
    1,
  );
});
