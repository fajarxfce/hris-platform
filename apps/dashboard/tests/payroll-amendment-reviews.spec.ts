import { expect, type Page, test } from "@playwright/test";
import { amendmentReviewId } from "./fixtures/payroll-amendment-reviews";
import { amendmentCalculationId, amendmentId } from "./fixtures/payroll-amendments";
import { calculationCompany } from "./fixtures/payroll-calculations";
import { companyIds } from "./identity-api";
import { installPayrollAmendmentReviewsApi } from "./payroll-amendment-reviews-api";

const context = `company=${calculationCompany}&year=2026&query=EMP`,
  proposal = `/payroll/amendments/${amendmentId()}?${context}`,
  reviews = `/payroll/amendments/${amendmentId()}/reviews?${context}`,
  review = `/payroll/amendment-reviews/${amendmentReviewId()}?${context}`,
  actionPath = (action: string) =>
    action === "submit"
      ? `/payroll/amendments/${amendmentId()}/reviews/new?${context}`
      : `/payroll/amendment-reviews/${amendmentReviewId()}/${action}?${context}`;
async function confirm(page: Page, action: string, reason = "Reviewed the retained correction") {
  await page.getByRole("textbox", { name: "Reason", exact: true }).fill(reason);
  await page.getByRole("button", { name: action, exact: true }).click();
}
async function openSaved(page: Page) {
  await page.getByRole("status").getByRole("link", { name: "View review", exact: true }).click();
}

test("submission, withdrawal and resubmission retain separate reviews and proposal evidence", async ({
  page,
}) => {
  const api = await installPayrollAmendmentReviewsApi(page);
  await page.goto(proposal);
  await page.getByRole("link", { name: "Amendment reviews", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("No reviews have been submitted");
  await page.getByRole("link", { name: "Submit for review", exact: true }).click();
  await page.getByRole("button", { name: "Submit for review", exact: true }).click();
  await expect(page.getByRole("textbox", { name: "Reason", exact: true })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(api.count("submit")).toBe(0);
  await confirm(page, "Submit for review");
  await expect(page.getByRole("status")).toContainText("Amendment submitted for review.");
  const first = [...api.records.keys()][0];
  expect(first).toBeTruthy();
  expect(first).not.toBe(amendmentId());
  expect(api.requests.find((row) => row.action === "submit")?.body).toEqual({
    expectedVersion: 1,
    calculationId: amendmentCalculationId,
    reason: "Reviewed the retained correction",
  });
  await openSaved(page);
  await expect(page).toHaveURL(new RegExp(`/amendment-reviews/${first}\\?`, "u"));
  await expect(page).toHaveURL(/year=2026.*query=EMP/u);
  await page.getByRole("link", { name: "Reviewed calculation", exact: true }).click();
  await expect(page).toHaveURL(/revision=1/u);
  await expect(page.getByRole("table", { name: "Monthly comparison", exact: true })).toContainText(
    "2026-09",
  );
  await page.goBack();
  await page.getByRole("link", { name: "Withdraw", exact: true }).click();
  await confirm(page, "Withdraw", "Recheck the correction");
  await expect(page.getByRole("status")).toContainText("Review withdrawn.");
  await openSaved(page);
  await page.getByRole("tab", { name: "History", exact: true }).click();
  await expect(page.getByRole("table", { name: "History", exact: true })).toContainText(
    "Recheck the correction",
  );
  await page
    .getByRole("button", { name: "View revision: Proposal revision 3", exact: true })
    .click();
  await expect(page).toHaveURL(/revision=3/u);
  await expect(
    page.getByRole("table", { name: "Observed assessments", exact: true }),
  ).toBeVisible();
  await page.goBack();
  await page.getByRole("link", { name: "All amendment reviews", exact: true }).click();
  await expect(page.getByRole("table", { name: "Amendment reviews", exact: true })).toContainText(
    "Withdrawn",
  );
  await page.getByRole("link", { name: "Submit for review", exact: true }).click();
  await confirm(page, "Submit for review", "Ready for a new independent review");
  await openSaved(page);
  await expect(
    page.getByRole("heading", { name: "Amendment review 2", exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "All amendment reviews", exact: true }).click();
  await page.getByRole("button", { name: "View review: Amendment review 1", exact: true }).click();
  await expect(page.getByRole("link", { name: /^(Approve|Reject|Withdraw)$/u })).toHaveCount(0);
  expect(api.records.size).toBe(2);
  expect(api.receipts.size).toBe(3);
  expect(api.identity.unhandled).toEqual([]);
});

test("independent stages keep the original calculation and recorded reviewers", async ({
  page,
}) => {
  const api = await installPayrollAmendmentReviewsApi(page, { role: "first", initial: true });
  await page.goto(review);
  await page.getByRole("tab", { name: "Approval stages", exact: true }).click();
  await expect(page.getByRole("table", { name: "Approval stages", exact: true })).toContainText(
    "Payroll Reviewer Two",
  );
  await page.getByRole("link", { name: "Approve", exact: true }).click();
  await page.getByRole("button", { name: "Approve", exact: true }).click();
  await expect(page.getByRole("textbox", { name: "Reason", exact: true })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(api.count("decide")).toBe(0);
  await confirm(page, "Approve", "First independent check");
  await openSaved(page);
  await expect(page.getByRole("link", { name: "Approve", exact: true })).toHaveCount(0);
  await page.getByRole("tab", { name: "Approval stages", exact: true }).click();
  await expect(page.getByRole("table", { name: "Approval stages", exact: true })).toContainText(
    "Completed",
  );
  api.setRole("second");
  await page.reload();
  await page.getByRole("link", { name: "Approve", exact: true }).click();
  await confirm(page, "Approve", "Second independent check");
  await openSaved(page);
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "Approved",
  );
  await page.getByRole("tab", { name: "History", exact: true }).click();
  await expect(page.getByRole("table", { name: "History", exact: true })).toContainText(
    "Payroll Reviewer One",
  );
  await expect(page.getByRole("table", { name: "History", exact: true })).toContainText(
    "Second independent check",
  );
  await page.getByRole("link", { name: "View proposal", exact: true }).click();
  await expect(page).toHaveURL(/revision=1/u);
  expect(api.requests.filter((row) => row.action === "decide").map((row) => row.body)).toEqual([
    {
      expectedVersion: 2,
      expectedReviewVersion: 0,
      expectedApprovalVersion: 0,
      decision: "APPROVE",
      reason: "First independent check",
    },
    {
      expectedVersion: 3,
      expectedReviewVersion: 1,
      expectedApprovalVersion: 1,
      decision: "APPROVE",
      reason: "Second independent check",
    },
  ]);
});

test("rejection retains the reason and removes actions against the rejected proposal", async ({
  page,
}) => {
  const api = await installPayrollAmendmentReviewsApi(page, { role: "first", initial: true });
  await page.goto(actionPath("reject"));
  await confirm(page, "Reject", "Correct the monthly amount");
  await openSaved(page);
  await expect(page.getByRole("region", { name: "Overview", exact: true })).toContainText(
    "Rejected",
  );
  await expect(page.getByRole("link", { name: /^(Approve|Reject|Withdraw)$/u })).toHaveCount(0);
  await page.getByRole("tab", { name: "History", exact: true }).click();
  await expect(page.getByRole("table", { name: "History", exact: true })).toContainText(
    "Correct the monthly amount",
  );
  api.setRole("author");
  await page.goto(reviews);
  await expect(page.getByRole("table", { name: "Amendment reviews", exact: true })).toContainText(
    "Rejected",
  );
  await expect(page.getByRole("link", { name: "Submit for review", exact: true })).toHaveCount(0);
});

test("blocked assignments stay visible without offering reviewer decisions", async ({ page }) => {
  await installPayrollAmendmentReviewsApi(page, { role: "first", initial: true, blocked: true });
  await page.goto(review);
  await expect(page.getByRole("status")).toContainText(
    "An administrator must assign an eligible reviewer",
  );
  await expect(page.getByRole("link", { name: /^(Approve|Reject)$/u })).toHaveCount(0);
  await page.getByRole("tab", { name: "Approval stages", exact: true }).click();
  await expect(page.getByRole("table", { name: "Approval stages", exact: true })).toContainText(
    "Assignment required",
  );
});

for (const role of ["author", "publisher", "reader"] as const)
  test(`${role} has no independent review command authority`, async ({ page }) => {
    const api = await installPayrollAmendmentReviewsApi(page, { role, initial: true });
    await page.goto(review);
    if (role === "reader")
      await expect(page.getByRole("alert")).toContainText("You do not have access");
    else await expect(page.getByRole("region", { name: "Overview", exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: /^(Approve|Reject)$/u })).toHaveCount(0);
    const reads = api.count("review");
    await page.goto(actionPath("approve"));
    await expect(page.getByRole("alert")).toContainText("You do not have access");
    expect(api.count("review")).toBe(reads);
    expect(api.count("decide")).toBe(0);
  });

for (const action of ["submit", "approve", "reject", "withdraw"] as const)
  test(`${action} recovers the original command through response loss, MFA and a later conflict`, async ({
    page,
  }) => {
    const role = action === "approve" || action === "reject" ? "first" : "author",
      method = action === "approve" || action === "reject" ? "decide" : action,
      label =
        action === "submit"
          ? "Submit for review"
          : action === "approve"
            ? "Approve"
            : action === "reject"
              ? "Reject"
              : "Withdraw",
      api = await installPayrollAmendmentReviewsApi(page, { role, initial: action !== "submit" });
    await page.goto(actionPath(action));
    api.loseNext(method);
    await confirm(page, label);
    const retry = page.getByRole("button", { name: "Retry original command", exact: true });
    await expect(retry).toBeVisible();
    api.identity.expireMfa();
    api.failNext(method, "mfa_required", 403);
    await retry.click();
    await page
      .getByRole("alert")
      .getByRole("button", { name: "Account verification", exact: true })
      .click();
    const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
    await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
    await verification.getByRole("button", { name: "Verify", exact: true }).click();
    await expect(verification).toHaveCount(0);
    await expect(page.getByRole("textbox", { name: "Reason", exact: true })).toHaveCount(0);
    await expect(page.getByText("Amendment employee 1", { exact: true })).toHaveCount(0);
    api.failNext(method, "payroll_amendment_review_not_current");
    await retry.click();
    await expect(retry).toBeEnabled();
    await retry.click();
    await expect(page.getByRole("status")).toContainText(
      action === "submit"
        ? "Amendment submitted for review."
        : action === "withdraw"
          ? "Review withdrawn."
          : "Decision recorded.",
    );
    const writes = api.requests.filter((row) => row.action === method);
    expect(writes).toHaveLength(4);
    for (const write of writes) expect(write).toEqual(writes[0]);
    expect(api.receipts.size).toBe(1);
    expect(api.count(action === "submit" ? "get" : "review")).toBe(1);
    await expect(page.getByRole("main")).not.toContainText("PRIVATE amendment review diagnostic");
  });

test("a changed approval version requires protected reload before another decision", async ({
  page,
}) => {
  const api = await installPayrollAmendmentReviewsApi(page, { role: "first", initial: true });
  await page.goto(actionPath("approve"));
  await expect(page.getByRole("textbox", { name: "Reason", exact: true })).toBeVisible();
  const raw = api.records.get(amendmentReviewId());
  if (!raw) throw new Error("Review required");
  raw.approval.version++;
  await confirm(page, "Approve");
  await expect(page.getByRole("button", { name: "Approve", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  expect(api.count("review")).toBe(1);
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByRole("textbox", { name: "Reason", exact: true })).toHaveValue("");
  await confirm(page, "Approve", "Reviewed the current assignment");
  await expect(page.getByRole("status")).toContainText("Decision recorded.");
  const writes = api.requests.filter((row) => row.action === "decide");
  expect(writes[1]?.body?.expectedApprovalVersion).toBe(1);
  expect(writes[1]?.operation).not.toBe(writes[0]?.operation);
});

test("pending commands cannot display their receipt in a replacement company", async ({ page }) => {
  const api = await installPayrollAmendmentReviewsApi(page, { initial: true }),
    held = api.holdNext("withdraw");
  try {
    await page.goto(actionPath("withdraw"));
    await confirm(page, "Withdraw");
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("No amendments");
    held.release();
    await expect(page.getByText("Review withdrawn.", { exact: true })).toHaveCount(0);
    await expect(page.getByText("Amendment employee 1", { exact: true })).toHaveCount(0);
  } finally {
    held.release();
  }
});

test("revoked reviewer authority clears private evidence and entered reasons", async ({ page }) => {
  const api = await installPayrollAmendmentReviewsApi(page, { role: "first", initial: true });
  await page.goto(actionPath("approve"));
  api.failNext("decide", "access_denied", 403);
  await confirm(page, "Approve");
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  await expect(page.getByRole("textbox", { name: "Reason", exact: true })).toHaveCount(0);
  await expect(page.getByText("Amendment employee 1", { exact: true })).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Retry original command", exact: true }),
  ).toHaveCount(0);
  expect(api.receipts.size).toBe(0);
});

test("narrow dark Indonesian review and decision retain state without reacquisition", async ({
  page,
}) => {
  const api = await installPayrollAmendmentReviewsApi(page, { role: "first", initial: true });
  await page.goto(review);
  await page.getByRole("tab", { name: "Approval stages", exact: true }).click();
  await expect(page.getByRole("table", { name: "Approval stages", exact: true })).toBeVisible();
  const reads = api.count("review");
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("table", { name: "Tahapan persetujuan", exact: true })).toContainText(
    "Payroll Reviewer One",
  );
  expect(api.count("review")).toBe(reads);
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    .toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-payroll-amendment-review-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("link", { name: "Setujui", exact: true }).click();
  await page.getByRole("textbox", { name: "Alasan", exact: true }).fill("Independent review");
  const acquired = api.count("review");
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  await expect(page.getByRole("textbox", { name: "Reason", exact: true })).toHaveValue(
    "Independent review",
  );
  expect(api.count("review")).toBe(acquired);
});
