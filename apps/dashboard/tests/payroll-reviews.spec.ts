import { expect, test } from "@playwright/test";
import { calculationCompany, calculationId } from "./fixtures/payroll-calculations";
import { reviewId } from "./fixtures/payroll-reviews";
import { installPayrollReviewsApi } from "./payroll-reviews-api";

const detail = `/payroll/reviews/${reviewId}?company=${calculationCompany}`,
  submission = `/payroll/runs/${calculationId}/reviews/new?company=${calculationCompany}`;
test("run navigation opens exact totals, staged reviewers and history without tab requests", async ({
  page,
}) => {
  const api = await installPayrollReviewsApi(page);
  await page.goto(`/payroll/runs/${calculationId}?company=${calculationCompany}`);
  await page.getByRole("link", { name: "Payroll reviews", exact: true }).click();
  await expect(page.getByRole("region", { name: "Retained totals", exact: true })).toContainText(
    "-IDR",
  );
  await page.getByRole("button", { name: "View review: Payroll review 1", exact: true }).click();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "Payroll Preparer",
  );
  const reads = api.reads.length;
  await page.getByRole("tab", { name: "Approval stages", exact: true }).click();
  await expect(page.getByRole("table", { name: "Approval stages", exact: true })).toContainText(
    "Payroll Reviewer Two",
  );
  await page.getByRole("tab", { name: "History", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "History", exact: true }).getByRole("row"),
  ).toHaveCount(2);
  expect(api.reads).toHaveLength(reads);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-payroll-review-history-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});
test("submission recovers its original command across a lost response and a later conflict", async ({
  page,
}) => {
  const api = await installPayrollReviewsApi(page, { initial: false });
  await page.goto(submission);
  await page.getByLabel("Reason", { exact: true }).fill("Review retained September payroll");
  const reads = api.reads.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue(
    "Review retained September payroll",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  expect(api.reads).toHaveLength(reads);
  api.dropNext();
  await page.getByRole("button", { name: "Submit for review", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Retry original command", exact: true }),
  ).toBeVisible();
  api.failNext("stale_version", 409);
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Retry original command", exact: true }),
  ).toBeEnabled();
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Payroll submitted for review.");
  expect(api.writes).toHaveLength(3);
  expect(api.writes[1]).toEqual(api.writes[0]);
  expect(api.writes[2]).toEqual(api.writes[0]);
  expect(api.records.size).toBe(1);
  await page.getByRole("link", { name: "View review", exact: true }).click();
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "Review retained September payroll",
  );
});
test("a reassignment conflict requires re-review before recording a stage decision", async ({
  page,
}) => {
  const api = await installPayrollReviewsApi(page, { role: "reviewer" });
  await page.goto(detail);
  await page.getByRole("link", { name: "Approve", exact: true }).click();
  await expect(page.getByLabel("Reason (optional)", { exact: true })).toBeVisible();
  api.reassign();
  await page.getByRole("button", { name: "Approve", exact: true }).click();
  await expect(page.getByRole("button", { name: "Reload review", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("button", { name: "Approve", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Decision recorded.");
  expect(api.writes[0]?.body.expectedApprovalVersion).toBe(0);
  expect(api.writes[1]?.body.expectedApprovalVersion).toBe(1);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
  await page.getByRole("status").getByRole("link", { name: "View review", exact: true }).click();
  await expect(page.getByRole("link", { name: "Approve", exact: true })).toHaveCount(0);
  await page.getByRole("tab", { name: "Approval stages", exact: true }).click();
  await expect(page.getByRole("table", { name: "Approval stages", exact: true })).toContainText(
    "Completed",
  );
  await expect(page.getByRole("table", { name: "Approval stages", exact: true })).toContainText(
    "Awaiting decision",
  );
});
test("rejection requires a reason and prevents resubmission of the same run", async ({ page }) => {
  const api = await installPayrollReviewsApi(page, { role: "reviewer" });
  await page.goto(detail);
  await page.getByRole("link", { name: "Reject", exact: true }).click();
  await page.getByRole("button", { name: "Reject", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveAttribute("aria-invalid", "true");
  expect(api.writes).toHaveLength(0);
  await page.getByLabel("Reason", { exact: true }).fill("Review withholding evidence");
  await page.getByRole("button", { name: "Reject", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Decision recorded.");
  await page.getByRole("status").getByRole("link", { name: "View review", exact: true }).click();
  await page.getByRole("link", { name: "All reviews", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Start a new calculation");
});
test("withdrawal recovers a lost receipt and retains history for another submission", async ({
  page,
}) => {
  const api = await installPayrollReviewsApi(page);
  await page.goto(detail);
  await page.getByRole("link", { name: "Withdraw", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Review the reference again");
  api.dropNext();
  await page.getByRole("button", { name: "Withdraw", exact: true }).click();
  await page.getByRole("button", { name: "Retry original command", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Review withdrawn.");
  expect(api.writes[1]).toEqual(api.writes[0]);
  await page.getByRole("status").getByRole("link", { name: "View review", exact: true }).click();
  await page.getByRole("tab", { name: "History", exact: true }).click();
  await expect(page.getByRole("table", { name: "History", exact: true })).toContainText(
    "Review the reference again",
  );
  await page.getByRole("link", { name: "All reviews", exact: true }).click();
  await expect(page.getByRole("link", { name: "Submit for review", exact: true })).toBeVisible();
});
test("read-only and blocked review states do not offer decisions", async ({ page }) => {
  const api = await installPayrollReviewsApi(page, { role: "reader", blocked: true });
  await page.goto(detail);
  await expect(page.getByRole("status")).toContainText("An administrator must assign");
  await expect(page.getByRole("link", { name: /^(Approve|Reject|Withdraw)$/u })).toHaveCount(0);
  await page.goto(`/payroll/reviews/${reviewId}/approve?company=${calculationCompany}`);
  await expect(page.getByRole("alert")).toHaveText("You do not have access to this resource.");
  expect(api.writes).toHaveLength(0);
});
test("company replacement cancels an old detail read without publishing its financial data", async ({
  page,
}) => {
  const api = await installPayrollReviewsApi(page);
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Retained totals", exact: true })).toBeVisible();
  const held = api.holdNext();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await held.entered.promise;
  try {
    await page
      .getByRole("combobox", { name: "Company", exact: true })
      .selectOption("10000000-0000-4000-8000-000000000002");
    await expect(page.getByRole("heading", { name: "Payroll periods", exact: true })).toBeVisible();
  } finally {
    held.released.resolve();
  }
  await expect(page.getByText("Payroll Preparer", { exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});
test("grant loss withdraws totals and dark narrow layout stays within the viewport", async ({
  page,
}) => {
  const api = await installPayrollReviewsApi(page);
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Retained totals", exact: true })).toBeVisible();
  const reads = api.reads.length;
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(api.reads).toHaveLength(reads);
  await page.screenshot({
    path: "../../.work/dashboard-payroll-review-dark-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
  api.failNext("access_denied");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("region", { name: "Retained totals", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Withdraw", exact: true })).toHaveCount(0);
  expect(await page.content()).not.toContain("PRIVATE TECHNICAL DIAGNOSTIC");
});

test("parent navigation waits for the owning run while review detail is pending", async ({
  page,
}) => {
  const api = await installPayrollReviewsApi(page),
    held = api.holdNext();
  await page.goto(detail);
  await held.entered.promise;
  try {
    await expect(page.getByRole("link", { name: "All reviews", exact: true })).toHaveCount(0);
  } finally {
    held.released.resolve();
  }
  await page.getByRole("link", { name: "All reviews", exact: true }).click();
  await expect(page).toHaveURL(
    `/payroll/runs/${calculationId}/reviews?company=${calculationCompany}`,
  );
  await expect(page.getByRole("table", { name: "History", exact: true })).toBeVisible();
});
