import { expect, test } from "@playwright/test";
import { installExpenseClaimActionsApi } from "./expense-claim-actions-api";
import { expenseIdentifier } from "./fixtures/expense-claims";
import { companyIds } from "./identity-api";

const query = `company=${companyIds[0]}&from=2026-10-01&until=2026-10-31`;
const claim = `/expenses/claims/${expenseIdentifier(1)}`;
const actions = [
  { intent: "submit", label: "Submit claim", saved: "Claim submitted.", status: "PENDING" },
  { intent: "cancel", label: "Cancel draft", saved: "Draft cancelled.", status: "CANCELLED" },
  {
    intent: "withdraw",
    label: "Withdraw submission",
    saved: "Submission withdrawn.",
    status: "DRAFT",
  },
] as const;

test("reviews current item references, validates a reason and submits the reviewed claim once", async ({
  page,
}) => {
  const api = await installExpenseClaimActionsApi(page);
  await page.goto(`${claim}?${query}`);
  await page.getByRole("button", { name: "Submit claim", exact: true }).click();
  await expect(page.getByRole("table", { name: "Items", exact: true })).toContainText(
    "Business travel",
  );
  await page.getByRole("button", { name: "View item: Return train ticket", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Item details", exact: true });
  await expect(panel).toContainText("receipt-1.pdf");
  await expect(panel).toContainText("Operations");
  await panel.getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("button", { name: "Submit claim", exact: true }).click();
  await expect(
    page.getByText("Enter a reason of up to 1,000 characters.", { exact: true }),
  ).toBeVisible();
  expect(api.actionWrites).toHaveLength(0);
  await page.getByLabel("Reason", { exact: true }).fill("  Submit reviewed travel  ");
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-expense-submit.png",
    fullPage: true,
    animations: "disabled",
  });
  const held = api.holdAction();
  try {
    await page.getByRole("button", { name: "Submit claim", exact: true }).click();
    await held.started;
    await expect(page.getByRole("button", { name: "Submit claim", exact: true })).toBeDisabled();
    await expect(page.getByLabel("Reason", { exact: true })).toHaveAttribute("readonly", "");
    expect(api.actionWrites).toHaveLength(1);
    expect(api.actionWrites[0]?.body).toMatchObject({
      expectedVersion: 0,
      reason: "Submit reviewed travel",
    });
    held.release();
    await expect(page.getByRole("status")).toContainText("Claim submitted.");
    await page.getByRole("link", { name: "Claim details", exact: true }).first().click();
    await expect(
      page.getByRole("button", { name: "Withdraw submission", exact: true }),
    ).toBeVisible();
    expect(api.records.get(companyIds[0])?.[0]?.claim).toMatchObject({
      version: 1,
      status: "PENDING",
      submissionCount: 1,
    });
    expect(api.actionCommits).toBe(1);
    expect(api.identity.unhandled).toEqual([]);
  } finally {
    held.release();
  }
});

for (const action of actions) {
  test(`${action.intent} retains the original body and operation after a lost committed response`, async ({
    page,
  }) => {
    const api = await installExpenseClaimActionsApi(page, {
      pending: action.intent === "withdraw",
    });
    await page.goto(`${claim}/${action.intent}?${query}`);
    await page.getByLabel("Reason", { exact: true }).fill("Travel review confirmed");
    api.loseNextAction();
    await page.getByRole("button", { name: action.label, exact: true }).click();
    const retry = page.getByRole("button", { name: "Retry original action", exact: true });
    await expect(retry).toBeVisible();
    await expect(page.getByRole("button", { name: action.label, exact: true })).toBeDisabled();
    await retry.click();
    await expect(page.getByRole("status")).toContainText(action.saved);
    expect(api.actionWrites).toHaveLength(2);
    expect(api.actionWrites[1]).toEqual(api.actionWrites[0]);
    expect(api.actionCommits).toBe(1);
    expect(api.records.get(companyIds[0])?.[0]?.claim.status).toBe(action.status);
    if (action.intent !== "submit") expect(api.draftingReads).toHaveLength(0);
    if (action.intent === "withdraw") {
      expect(api.actionWrites[0]?.path).toContain(
        `/submissions/${expenseIdentifier(1001)}/withdraw`,
      );
      expect(api.records.get(companyIds[0])?.[0]?.claim.latestSubmissionId).toBe(
        expenseIdentifier(1001),
      );
    }
  });
}

test("lost submission remains recoverable through MFA and a later version conflict", async ({
  page,
}) => {
  const api = await installExpenseClaimActionsApi(page);
  await page.goto(`${claim}/submit?${query}`);
  await page.getByLabel("Reason", { exact: true }).fill("Submit travel");
  api.loseNextAction();
  await page.getByRole("button", { name: "Submit claim", exact: true }).click();
  const retry = page.getByRole("button", { name: "Retry original action", exact: true });
  await expect(retry).toBeVisible();
  api.identity.expireMfa();
  api.rejectAction("mfa_required", 403);
  await retry.click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("North Employee");
  await expect(page.getByRole("table", { name: "Items", exact: true })).toHaveCount(0);
  api.rejectAction("stale_version");
  await retry.click();
  await expect(retry).toBeVisible();
  await retry.click();
  await expect(page.getByRole("status")).toContainText("Claim submitted.");
  expect(api.actionCommits).toBe(1);
  expect(api.actionWrites).toHaveLength(4);
  for (const write of api.actionWrites) expect(write).toEqual(api.actionWrites[0]);
});

test("a changed claim requires a protected reload and a new reviewed version", async ({ page }) => {
  const api = await installExpenseClaimActionsApi(page);
  await page.goto(`${claim}/submit?${query}`);
  await page.getByLabel("Reason", { exact: true }).fill("My original review");
  const record = api.records.get(companyIds[0])?.[0];
  if (!record) throw new Error("Fixture claim is missing");
  record.claim.version = 1;
  record.claim.draftRevision = 1;
  record.claim.draft.revision = 1;
  record.claim.draft.title = "Updated travel review";
  record.history.items.unshift({
    version: 1,
    draftRevision: 1,
    kind: "DRAFT_SAVED",
    status: "DRAFT",
    actorId: record.claim.createdBy,
    reason: "Concurrent correction",
    recordedAt: "2026-10-13T12:00:00Z",
    submissionId: null,
  });
  await page.getByRole("button", { name: "Submit claim", exact: true }).click();
  await expect(page.getByRole("button", { name: "Submit claim", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveValue("My original review");
  await page.getByRole("button", { name: "Reload review", exact: true }).click();
  await page.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Updated travel review", exact: true }),
  ).toBeVisible();
  await page.getByLabel("Reason", { exact: true }).fill("Reviewed corrected claim");
  await page.getByRole("button", { name: "Submit claim", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Claim submitted.");
  expect(api.actionWrites.map((write) => write.body.expectedVersion)).toEqual([0, 1]);
  expect(api.actionWrites[1]?.operation).not.toBe(api.actionWrites[0]?.operation);
});

test("unready evidence reports a policy failure and permits an explicit new confirmation", async ({
  page,
}) => {
  const api = await installExpenseClaimActionsApi(page);
  await page.goto(`${claim}/submit?${query}`);
  await page.getByLabel("Reason", { exact: true }).fill("Review receipts");
  api.rejectAction("expense_receipt_not_ready");
  await page.getByRole("button", { name: "Submit claim", exact: true }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("main")).not.toContainText("PRIVATE EMPLOYEE DATA");
  await expect(page.getByRole("button", { name: "Submit claim", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "Submit claim", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Claim submitted.");
  expect(api.actionWrites).toHaveLength(2);
  expect(api.actionWrites[1]?.operation).not.toBe(api.actionWrites[0]?.operation);
  expect(api.actionCommits).toBe(1);
});

test("an empty draft may be cancelled and cannot be submitted", async ({ page }) => {
  const api = await installExpenseClaimActionsApi(page),
    record = api.records.get(companyIds[0])?.[0];
  if (!record) throw new Error("Fixture claim is missing");
  record.claim.draft.lines = [];
  record.claim.draft.totalAmount = "0.00";
  await page.goto(`${claim}?${query}`);
  await expect(page.getByRole("button", { name: "Cancel draft", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Submit claim", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Cancel draft", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Duplicate draft");
  await page.getByRole("button", { name: "Cancel draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Draft cancelled.");
  expect(api.draftingReads).toHaveLength(0);
});

test("late submission references cannot cross a company change", async ({ page }) => {
  const api = await installExpenseClaimActionsApi(page),
    held = api.holdDraftRead("references");
  try {
    await page.goto(`${claim}/submit?${query}`);
    await held.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Expense claims", exact: true })).toContainText(
      "South",
    );
    held.release();
    await expect(page.getByRole("main")).not.toContainText("North Employee");
    expect(api.actionWrites).toHaveLength(0);
  } finally {
    held.release();
  }
});

test("manager access is required before acquiring an action review", async ({ page }) => {
  const api = await installExpenseClaimActionsApi(page, { permissions: ["expenses.read"] });
  await page.goto(`${claim}/submit?${query}`);
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByLabel("Reason", { exact: true })).toHaveCount(0);
  expect(api.reads).toHaveLength(0);
  expect(api.draftingReads).toHaveLength(0);
});

test("review keeps its reason through item inspection, dark appearance and Indonesian layout", async ({
  page,
}) => {
  const api = await installExpenseClaimActionsApi(page);
  await page.goto(`${claim}/submit?${query}`);
  await page.getByLabel("Reason", { exact: true }).fill("Review travel receipt");
  await page.getByRole("button", { name: "View item: Return train ticket", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Close", exact: true }).click();
  await page.getByRole("link", { name: "Claim details", exact: true }).click();
  await page.getByRole("button", { name: "Stay on this page", exact: true }).click();
  await page.setViewportSize({ width: 760, height: 900 });
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Alasan", { exact: true })).toHaveValue("Review travel receipt");
  await page.getByRole("button", { name: "Lihat item: Return train ticket", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("receipt-1.pdf");
  await page.screenshot({
    path: "../../.work/dashboard-expense-submit-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(api.actionWrites).toHaveLength(0);
});
