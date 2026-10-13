import { readFile } from "node:fs/promises";
import { expect, type Page, test } from "@playwright/test";
import { installExpenseDecisionsApi } from "./expense-decisions-api";
import { expenseIdentifier, expenseReceiptBytes } from "./fixtures/expense-claims";
import { companyIds } from "./identity-api";

const query = `company=${companyIds[0]}&from=2026-10-01&until=2026-10-31`;
const path = `/expenses/submissions/${expenseIdentifier(1001)}`;
const submitted = `${path}?${query}`;
async function open(page: Page, intent = "approve") {
  await page.goto(`${path}/${intent}?${query}`);
  await expect(page.getByRole("table", { name: "Items", exact: true })).toContainText(
    "Return train ticket",
  );
}

test("read permission exposes evidence but neither decision commands nor their direct routes", async ({
  page,
}) => {
  const api = await installExpenseDecisionsApi(page, ["expenses.read"]);
  await page.goto(submitted);
  await expect(page.getByRole("region", { name: "Expense submission", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Approve", exact: true })).toHaveCount(0);
  const reads = api.decisionReads.length;
  await page.goto(`${path}/approve?${query}`);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.decisionReads).toHaveLength(reads);
  expect(api.writes).toEqual([]);
});

test("server availability gates reviewers independently of their broad permission", async ({
  page,
}) => {
  const api = await installExpenseDecisionsApi(page);
  api.record.availableActions = [];
  await page.goto(submitted);
  await expect(page.getByRole("button", { name: "Approve", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "View claim", exact: true })).toHaveCount(0);
  await page.goto(`${path}/approve?${query}`);
  await expect(page.getByRole("alert")).toContainText(
    "You cannot decide this submission at its current stage.",
  );
  await expect(page.getByRole("button", { name: "Approve expense", exact: true })).toHaveCount(0);
  expect(api.writes).toEqual([]);
});

test("intermediate approval records its stage without claiming that reimbursement is complete", async ({
  page,
}) => {
  const api = await installExpenseDecisionsApi(page);
  api.record.approval.stages.push([expenseIdentifier(901)]);
  await page.goto(submitted);
  await page.getByRole("button", { name: "Approve", exact: true }).click();
  await expect(page.getByRole("button", { name: "Approve expense", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "Approve expense", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Decision recorded.");
  expect(api.writes[0]?.body).toEqual({
    expectedVersion: 23,
    expectedApprovalVersion: 0,
    decision: "APPROVE",
    reason: "",
    acknowledgeDuplicates: false,
  });
  expect(api.record.claimStatus).toBe("PENDING");
  await page.getByRole("link", { name: "View submission", exact: true }).click();
  await expect(page).toHaveURL(submitted);
  await expect(page.getByRole("region", { name: "Expense submission", exact: true })).toContainText(
    "Pending",
  );
  await page.getByRole("tab", { name: "Approval", exact: true }).click();
  const stages = page.getByRole("table", { name: "Stage", exact: true });
  await expect(stages.getByRole("row")).toHaveCount(3);
  await expect(stages.getByRole("row").nth(1)).toContainText("Approved");
  await expect(stages.getByRole("row").nth(2)).toContainText("Pending");
  await expect(page.getByRole("button", { name: "Approve", exact: true })).toHaveCount(0);
  expect(api.commits).toBe(1);
});

for (const [intent, label, saved, status] of [
  ["reject", "Reject expense", "Submission rejected.", "REJECTED"],
  ["return", "Return expense", "Returned for correction.", "RETURNED"],
] as const)
  test(`${intent} requires a reason and records the observed claim and approval revisions`, async ({
    page,
  }) => {
    const api = await installExpenseDecisionsApi(page);
    await open(page, intent);
    await page.getByRole("button", { name: label, exact: true }).click();
    await expect(page.getByRole("alert")).toContainText("Enter a reason.");
    expect(api.writes).toEqual([]);
    await page.getByLabel("Reason", { exact: true }).fill("  Receipt needs correction  ");
    await page.getByRole("button", { name: label, exact: true }).click();
    await expect(page.getByRole("status")).toContainText(saved);
    expect(api.record.claimStatus).toBe(status);
    expect(api.record.approval.status).toBe("REJECTED");
    expect(api.writes[0]?.body).toEqual({
      expectedVersion: 23,
      expectedApprovalVersion: 0,
      decision: intent === "reject" ? "REJECT" : "RETURN",
      reason: "Receipt needs correction",
      acknowledgeDuplicates: false,
    });
    expect(api.commits).toBe(1);
  });

test("duplicate approval requires acknowledgement and permits evidence inspection without discarding the reason", async ({
  page,
}) => {
  const api = await installExpenseDecisionsApi(page);
  api.record.duplicateReceiptDigests = ["a".repeat(64)];
  await open(page);
  await page.getByRole("button", { name: "Approve expense", exact: true }).click();
  await expect(
    page.getByText("Confirm that the duplicate signals have been reviewed.", { exact: true }),
  ).toBeVisible();
  expect(api.writes).toEqual([]);
  await page.getByLabel("Reason", { exact: true }).fill("Distinct reimbursable portion confirmed");
  const reads = api.decisionReads.length;
  await page.getByRole("button", { name: "View item: Return train ticket", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Item details", exact: true });
  await expect(panel).toContainText("train-receipt.pdf");
  await expect(page.getByRole("button", { name: "Leave page", exact: true })).toHaveCount(0);
  const downloaded = page.waitForEvent("download");
  await panel.getByRole("button", { name: "Download: train-receipt.pdf", exact: true }).click();
  const download = await downloaded,
    file = await download.path();
  expect(file).not.toBeNull();
  if (file) expect(await readFile(file)).toEqual(Buffer.from(expenseReceiptBytes));
  await panel.getByRole("button", { name: "Close", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue(
    "Distinct reimbursable portion confirmed",
  );
  expect(api.decisionReads).toHaveLength(reads);
  await page
    .getByRole("checkbox", {
      name: "I have reviewed the duplicate signals and supporting receipts.",
      exact: true,
    })
    .check();
  await page.getByRole("button", { name: "Approve expense", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Decision recorded.");
  expect(api.writes[0]?.body.acknowledgeDuplicates).toBe(true);
  expect(api.commits).toBe(1);
});

test("lost-response recovery retains the original command across MFA and a later denial", async ({
  page,
}) => {
  const api = await installExpenseDecisionsApi(page);
  await open(page);
  await page.getByLabel("Reason", { exact: true }).fill("Original evidence review");
  const reads = api.decisionReads.length;
  api.loseNext();
  await page.getByRole("button", { name: "Approve expense", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Retry original operation", exact: true }),
  ).toBeVisible();
  api.identity.expireMfa();
  api.rejectWrite("mfa_required", 403);
  await page.getByRole("button", { name: "Retry original operation", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
  api.rejectWrite("not_assigned_approver");
  await page.getByRole("button", { name: "Retry original operation", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Retry original operation", exact: true }),
  ).toBeVisible();
  const held = api.holdWrite();
  try {
    await page.getByRole("button", { name: "Retry original operation", exact: true }).click();
    await held.started;
    await expect(page.getByRole("main")).not.toContainText("Sample Employee");
    await expect(page.getByRole("main")).not.toContainText("Original evidence review");
    held.release();
    await expect(page.getByRole("status")).toContainText("Decision recorded.");
  } finally {
    held.release();
  }
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(4);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
  expect(api.decisionReads).toHaveLength(reads);
  expect(api.identity.unhandled).toEqual([]);
});

test("approval reassignment requires explicit re-review before a new operation", async ({
  page,
}) => {
  const api = await installExpenseDecisionsApi(page);
  await open(page);
  await page.getByLabel("Reason", { exact: true }).fill("Old assignment");
  api.record.approval.version += 1;
  await page.getByRole("button", { name: "Approve expense", exact: true }).click();
  await expect(page.getByRole("button", { name: "Approve expense", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Stay on this page", exact: true })
    .click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("Old assignment");
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("");
  expect(api.writes).toHaveLength(1);
  await page.getByLabel("Reason", { exact: true }).fill("Current assignment checked");
  await page.getByRole("button", { name: "Approve expense", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Decision recorded.");
  expect(api.writes[1]?.body.expectedVersion).toBe(23);
  expect(api.writes[1]?.body.expectedApprovalVersion).toBe(1);
  expect(api.writes[1]?.operation).not.toBe(api.writes[0]?.operation);
});

test("revoked receipt access removes the reviewed evidence and blocks the decision", async ({
  page,
}) => {
  const api = await installExpenseDecisionsApi(page);
  await open(page);
  await page.getByRole("button", { name: "View item: Return train ticket", exact: true }).click();
  api.rejectNext("receipt", "expense_receipt_not_found");
  await page.getByRole("button", { name: "Download: train-receipt.pdf", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This receipt is unavailable");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Approve expense", exact: true })).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("Sample Employee");
  expect(api.writes).toEqual([]);
  await page.getByRole("button", { name: "Retry", exact: true }).click();
  await expect(page.getByRole("button", { name: "Approve expense", exact: true })).toBeEnabled();
});

test("company replacement cannot restore an obsolete held review", async ({ page }) => {
  const api = await installExpenseDecisionsApi(page),
    held = api.holdDecisionRead();
  try {
    await page.goto(`${path}/approve?${query}`);
    await held.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page).not.toHaveURL(/\/expenses\/submissions/u);
    held.release();
    await expect(page.getByRole("main")).not.toContainText("North travel reimbursement");
    expect(api.writes).toEqual([]);
  } finally {
    held.release();
  }
});

test("dark narrow review retains form input and evidence selection across locale changes", async ({
  page,
}) => {
  const api = await installExpenseDecisionsApi(page);
  await page.setViewportSize({ width: 760, height: 1000 });
  await open(page, "return");
  await page.getByLabel("Reason", { exact: true }).fill("Correct the receipt date");
  const reads = api.decisionReads.length;
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await expect(page.getByRole("button", { name: "Theme", exact: true })).toHaveText("Light");
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Correct the receipt date");
  expect(api.decisionReads).toHaveLength(reads);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-expense-decision-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Kembalikan pengajuan biaya", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Dikembalikan untuk koreksi.");
  expect(api.commits).toBe(1);
});
