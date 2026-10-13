import { randomUUID } from "node:crypto";
import { expect, type Page, test } from "@playwright/test";
import { companyDate } from "../src/core/presentation/dates/company-date";
import type { ExpenseDraftChangeDto } from "../src/features/expenses/data/models/expense-draft-change-dto";
import { createAdminCompany } from "./fixtures/admin-company";
import { authenticatorCode } from "./fixtures/authenticator";
import { confirmExpenseClaimAction } from "./fixtures/expense-claim-action";

async function reviewerSession(page: Page) {
  await page.goto("/");
  await page
    .getByLabel("Email", { exact: true })
    .fill("browser-expense-claim-reviewer@example.invalid");
  await page.getByLabel("Password", { exact: true }).fill("Browser-fixture-password-123!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByRole("button", { name: "Set up an authenticator", exact: true }).click();
  const key = page.locator(".app-enrollment-key code");
  await expect(key).toBeVisible();
  await page
    .getByLabel("Authenticator code", { exact: true })
    .fill(authenticatorCode((await key.textContent()) ?? ""));
  await page.getByRole("button", { name: "Verify", exact: true }).click();
  await page.getByRole("button", { name: "I have saved the codes", exact: true }).click();
}

test("real expense authoring and actions replay lost responses, preserve evidence, and support returned-claim correction", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  test.setTimeout(120_000);
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-expense-claim-admin@example.invalid",
    "CLAIM_BROWSER",
    "Browser Expense Claims",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  const base = `/api/v1/companies/${company}`,
    employee = randomUUID(),
    category = randomUUID();
  const reviewer = "a0000000-0000-4000-8000-000000000031";
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const today = companyDate("Asia/Jakarta", new Date());
  const created = await context.request.post(`${base}/employees`, {
    headers: headers(),
    data: {
      id: employee,
      employeeNumber: "EXPENSE-001",
      person: { id: randomUUID(), legalName: "Expense Fixture Employee", nationality: "ID" },
      terms: {
        effectiveFrom: "2025-01-01",
        startDate: "2025-01-01",
        status: "ACTIVE",
        contract: "PERMANENT",
      },
      reason: "Expense browser acceptance",
    },
  });
  expect(created.status()).toBe(200);
  const member = await context.request.put(`${base}/members/${reviewer}`, {
    headers: headers(),
    data: {
      active: true,
      permissions: ["company.read", "expenses.approve", "approvals.read"],
      reason: "Independent expense reviewer",
    },
  });
  expect(member.status()).toBe(200);
  const policy = {
    code: "BROWSER_TRAVEL",
    name: "Business travel",
    effectiveFrom: "2025-01-01",
    maximumLineAmount: "500000.25",
    maximumClaimAmount: "2000000.75",
    maximumAgeDays: 30,
    allowedContracts: ["PERMANENT"],
    receiptRequired: false,
    costCenterRequired: false,
    active: true,
    expectedVersion: null,
    reason: "Travel policy",
  };
  const savedPolicy = await context.request.put(`${base}/expenses/categories/${category}`, {
    headers: headers(),
    data: policy,
  });
  expect(savedPolicy.status()).toBe(200);
  const template = await context.request.put(`${base}/approvals/templates/${randomUUID()}`, {
    headers: headers(),
    data: {
      name: "Expense review",
      kind: "EXPENSE",
      effectiveFrom: "2025-01-01",
      stages: [{ assignment: "NAMED", accountIds: [reviewer] }],
      reason: "Independent review assignment",
    },
  });
  expect(template.status()).toBe(200);
  const creationWrites: { id: string; operation: string; body: ExpenseDraftChangeDto }[] = [];
  const draftRoute = `**${base}/expenses/claims/*/draft`;
  await page.route(draftRoute, async (route) => {
    const claimId = new URL(route.request().url()).pathname.split("/").at(-2);
    if (!claimId) throw new Error("Creation requires a claim ID");
    creationWrites.push({
      id: claimId,
      operation: route.request().headers()["idempotency-key"] ?? "",
      body: route.request().postDataJSON() as ExpenseDraftChangeDto,
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    expect(await response.json()).toMatchObject({ id: claimId, version: 0 });
    if (creationWrites.length === 1) return route.abort("failed");
    return route.fulfill({ response });
  });
  await page.goto(`/expenses/claims/new?company=${company}&from=${today}&until=${today}`);
  await page.getByRole("button", { name: "Select: Expense Fixture Employee", exact: true }).click();
  await page.getByLabel("Claim title", { exact: true }).fill("Customer visit");
  await page.getByLabel("Description", { exact: true }).fill("Transport to customer office");
  await page.getByLabel("Change note", { exact: true }).fill("Expense details confirmed");
  await page.getByRole("button", { name: "Add item", exact: true }).click();
  const itemEditor = page.getByRole("dialog", { name: "Item details", exact: true });
  await itemEditor.getByLabel("Expense date", { exact: true }).fill(today);
  await itemEditor.getByRole("button", { name: "Select category", exact: true }).click();
  await itemEditor.getByRole("button", { name: "Select: Business travel", exact: true }).click();
  await itemEditor.getByLabel("Amount (IDR)", { exact: true }).fill("150000.75");
  await itemEditor.getByLabel("Item description", { exact: true }).fill("Return train ticket");
  await itemEditor.getByRole("button", { name: "Add receipt", exact: true }).click();
  await expect(itemEditor.getByText("No matching records.", { exact: true })).toBeVisible();
  await itemEditor.getByRole("button", { name: "Back to item", exact: true }).click();
  await itemEditor.getByRole("button", { name: "Apply item", exact: true }).click();
  await page
    .getByRole("heading", { name: "New expense claim", exact: true })
    .scrollIntoViewIfNeeded();
  await page.screenshot({
    path: "../../.work/dashboard-expense-draft-real.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await page.getByRole("button", { name: "Retry original save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Draft saved.");
  await page.unroute(draftRoute);
  const createdDraft = creationWrites[0];
  if (!createdDraft) throw new Error("Draft creation was not captured");
  const claim = createdDraft.id,
    draft = createdDraft.body;
  expect(creationWrites).toHaveLength(2);
  expect(creationWrites[1]).toEqual(createdDraft);
  expect(draft).toMatchObject({
    employmentId: employee,
    expectedVersion: null,
    lines: [{ categoryId: category, amount: "150000.75" }],
  });
  expect(
    await (await context.request.get(`${base}/expenses/claims/${claim}/review`)).json(),
  ).toMatchObject({
    claim: { version: 0 },
    history: { items: [{ version: 0, kind: "DRAFT_SAVED" }] },
  });
  const revised = await context.request.put(`${base}/expenses/claims/${claim}/draft`, {
    headers: headers(),
    data: { ...draft, title: "Customer visit reimbursement", expectedVersion: 0 },
  });
  expect(revised.status()).toBe(200);
  const submission = await confirmExpenseClaimAction(page, {
    company,
    claimId: claim,
    action: "submit",
    expectedVersion: 1,
    reason: "Submit travel reimbursement",
    loseResponse: true,
  });
  if (!submission) throw new Error("Submission is required for review");
  const revisedPolicy = await context.request.put(`${base}/expenses/categories/${category}`, {
    headers: headers(),
    data: {
      ...policy,
      expectedVersion: 0,
      effectiveFrom: today,
      name: "Revised travel policy",
      maximumLineAmount: "400000.50",
      reason: "Updated future claims",
    },
  });
  expect(revisedPolicy.status()).toBe(200);
  const other = await context.request.post("/api/v1/companies", {
    headers: headers(),
    data: { code: "CLAIM_OTHER", name: "Other Expense Claims", timezone: "Asia/Jakarta" },
  });
  expect(other.status()).toBe(200);
  const otherCompany = ((await other.json()) as { id: string }).id;
  expect(
    (
      await context.request.get(`/api/v1/companies/${otherCompany}/expenses/claims/${claim}/review`)
    ).status(),
  ).toBe(404);
  const detail = `/expenses/claims/${claim}?company=${company}&from=${today}&until=${today}`;
  await page.goto(`/expenses/claims?company=${company}&from=${today}&until=${today}`);
  const directory = page.getByRole("table", { name: "Expense claims", exact: true });
  await expect(directory).toContainText("Expense Fixture Employee");
  await expect(directory).toContainText("150,000.75");
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-expense-claims-real-directory.png",
    fullPage: true,
    animations: "disabled",
  });
  await page
    .getByRole("button", { name: "View: Customer visit reimbursement", exact: true })
    .click();
  await expect(page.getByRole("region", { name: "Current claim", exact: true })).toContainText(
    "Pending",
  );
  await page.getByRole("tab", { name: "History", exact: true }).click();
  const history = page.getByRole("table", { name: "History", exact: true });
  await expect(history.getByRole("row")).toHaveCount(4);
  await expect(history.getByRole("row").nth(1)).toContainText("Submitted");
  await page.getByRole("tab", { name: "Submissions", exact: true }).click();
  await page.getByRole("button", { name: "View submission: Submission 1", exact: true }).click();
  await expect(page.getByRole("region", { name: "Expense submission", exact: true })).toContainText(
    "Expense Fixture Employee",
  );
  await page.getByRole("button", { name: "View item: Return train ticket", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("Business travel (BROWSER_TRAVEL)");
  await expect(page.getByRole("dialog")).toContainText("500,000.25");
  await expect(page.getByRole("dialog")).not.toContainText("Revised travel policy");
  await page.screenshot({
    path: "../../.work/dashboard-expense-claims-real-item.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Close", exact: true }).click();
  const evidence = (await (
    await context.request.get(`${base}/expenses/submissions/${submission}`)
  ).json()) as { approval: { id: string } };
  if (!baseURL) throw new Error("Integration base URL is required");
  const reviewerContext = await browser.newContext({ baseURL });
  try {
    const reviewPage = await reviewerContext.newPage();
    await reviewerSession(reviewPage);
    await reviewPage.goto(`/approvals/${evidence.approval.id}?company=${company}`);
    await reviewPage.getByRole("link", { name: "View source request", exact: true }).click();
    await expect(reviewPage.getByRole("table", { name: "Items", exact: true })).toContainText(
      "Business travel",
    );
    expect(
      (await reviewerContext.request.get(`${base}/expenses/claims/${claim}/review`)).status(),
    ).toBe(404);
    await expect(reviewPage.getByRole("link", { name: "View claim", exact: true })).toHaveCount(0);
    const writes: { operation: string; body: unknown }[] = [];
    await reviewPage.route(
      `**${base}/expenses/submissions/${submission}/decisions`,
      async (route) => {
        writes.push({
          operation: route.request().headers()["idempotency-key"] ?? "",
          body: route.request().postDataJSON(),
        });
        const response = await route.fetch();
        expect(response.status()).toBe(200);
        expect(await response.json()).toMatchObject({ id: claim, version: 3 });
        if (writes.length === 1) return route.abort("failed");
        return route.fulfill({ response });
      },
    );
    await reviewPage.getByRole("button", { name: "Approve", exact: true }).click();
    await reviewPage.getByLabel("Reason", { exact: true }).fill("Reviewed submitted evidence");
    await reviewPage.evaluate(() => window.scrollTo(0, 0));
    await reviewPage.screenshot({
      path: "../../.work/dashboard-expense-decision-real.png",
      fullPage: true,
      animations: "disabled",
    });
    await reviewPage.getByRole("button", { name: "Approve expense", exact: true }).click();
    await expect(
      reviewPage.getByRole("button", { name: "Retry original operation", exact: true }),
    ).toBeVisible();
    await reviewPage.getByRole("button", { name: "Retry original operation", exact: true }).click();
    await expect(reviewPage.getByRole("status")).toContainText("Decision recorded.");
    expect(writes).toHaveLength(2);
    expect(writes[0]?.operation).toMatch(/^[a-f0-9-]{36}$/u);
    expect(writes[1]).toEqual(writes[0]);
    expect(writes[0]?.body).toEqual({
      expectedVersion: 2,
      expectedApprovalVersion: 0,
      decision: "APPROVE",
      reason: "Reviewed submitted evidence",
      acknowledgeDuplicates: false,
    });
    await reviewPage.getByRole("link", { name: "View submission", exact: true }).click();
    await expect(
      reviewPage.getByRole("region", { name: "Expense submission", exact: true }),
    ).toContainText("Approved");
    await reviewPage.getByRole("tab", { name: "Reviews", exact: true }).click();
    await expect(
      reviewPage.getByRole("table", { name: "Reviews", exact: true }).getByRole("row"),
    ).toHaveCount(2);
    await reviewPage.getByRole("button", { name: "View action: Stage 1", exact: true }).click();
    await expect(reviewPage.getByRole("dialog")).toContainText("Reviewed submitted evidence");
    await reviewPage.getByRole("button", { name: "Close", exact: true }).click();
    const approved = await reviewerContext.request.get(
      `${base}/expenses/submissions/${submission}`,
    );
    expect(await approved.json()).toMatchObject({
      claimVersion: 3,
      claimStatus: "APPROVED",
      availableActions: [],
      approval: { version: 1, status: "APPROVED" },
      reviews: [{ decision: "APPROVE" }],
    });

    const correctedClaim = randomUUID();
    const correctionDraft = {
      ...draft,
      title: "Receipt correction review",
      lines: draft.lines.map((item) => ({ ...item, id: randomUUID() })),
    };
    expect(
      (
        await context.request.put(`${base}/expenses/claims/${correctedClaim}/draft`, {
          headers: headers(),
          data: { ...correctionDraft, expectedVersion: null },
        })
      ).status(),
    ).toBe(200);
    const firstSubmission = await confirmExpenseClaimAction(page, {
      company,
      claimId: correctedClaim,
      action: "submit",
      expectedVersion: 0,
      reason: "Submit expense for review",
    });
    if (!firstSubmission) throw new Error("Submission is required for correction review");
    await reviewPage.goto(`/expenses/submissions/${firstSubmission}?company=${company}`);
    await reviewPage.getByRole("button", { name: "Return for correction", exact: true }).click();
    await reviewPage.getByLabel("Reason", { exact: true }).fill("Correct the item description");
    await reviewPage.getByRole("button", { name: "Return expense", exact: true }).click();
    await expect(reviewPage.getByRole("status")).toContainText("Returned for correction.");
    const returned = await reviewerContext.request.get(
      `${base}/expenses/submissions/${firstSubmission}`,
    );
    expect(await returned.json()).toMatchObject({
      current: true,
      claimVersion: 2,
      claimStatus: "RETURNED",
      availableActions: [],
      approval: { version: 1, status: "REJECTED" },
      reviews: [{ decision: "RETURN", reason: "Correct the item description" }],
    });
    await page.goto(
      `/expenses/claims/${correctedClaim}/edit?company=${company}&from=${today}&until=${today}`,
    );
    await expect(
      page.getByText("This claim was returned. Save your corrections before submitting again.", {
        exact: true,
      }),
    ).toBeVisible();
    await page.getByLabel("Description", { exact: true }).fill("Updated item description");
    await page.getByRole("button", { name: "Edit item: Return train ticket", exact: true }).click();
    const correctionEditor = page.getByRole("dialog", { name: "Item details", exact: true });
    await expect(correctionEditor).toContainText("Revised travel policy");
    await correctionEditor
      .getByLabel("Item description", { exact: true })
      .fill("Updated train ticket evidence");
    await correctionEditor.getByRole("button", { name: "Apply item", exact: true }).click();
    await page.getByLabel("Change note", { exact: true }).fill("Corrected reviewer findings");
    await page.getByRole("button", { name: "Save draft", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Draft saved.");
    expect(
      await (await context.request.get(`${base}/expenses/claims/${correctedClaim}/review`)).json(),
    ).toMatchObject({
      claim: {
        version: 3,
        draftRevision: 1,
        status: "DRAFT",
        draft: {
          lines: [
            { id: correctionDraft.lines[0]?.id, description: "Updated train ticket evidence" },
          ],
        },
      },
    });
    const correctedSubmission = await confirmExpenseClaimAction(page, {
      company,
      claimId: correctedClaim,
      action: "submit",
      expectedVersion: 3,
      reason: "Resubmit corrected expense",
    });
    if (!correctedSubmission) throw new Error("Corrected submission is required for review");
    await reviewPage.goto(`/expenses/submissions/${correctedSubmission}?company=${company}`);
    await reviewPage.getByRole("button", { name: "Reject", exact: true }).click();
    await reviewPage.getByLabel("Reason", { exact: true }).fill("Expense is not reimbursable");
    await reviewPage.getByRole("button", { name: "Reject expense", exact: true }).click();
    await expect(reviewPage.getByRole("status")).toContainText("Submission rejected.");
    const rejected = await reviewerContext.request.get(
      `${base}/expenses/submissions/${correctedSubmission}`,
    );
    expect(await rejected.json()).toMatchObject({
      number: 2,
      current: true,
      claimVersion: 5,
      claimStatus: "REJECTED",
      availableActions: [],
      approval: { version: 1, status: "REJECTED" },
      reviews: [{ decision: "REJECT", reason: "Expense is not reimbursable" }],
    });
    const original = await reviewerContext.request.get(
      `${base}/expenses/submissions/${firstSubmission}`,
    );
    expect(await original.json()).toMatchObject({
      number: 1,
      current: false,
      availableActions: [],
      description: "Transport to customer office",
      approval: { status: "REJECTED" },
      reviews: [{ decision: "RETURN" }],
    });
  } finally {
    await reviewerContext.close();
  }
  const withdrawnClaim = randomUUID();
  expect(
    (
      await context.request.put(`${base}/expenses/claims/${withdrawnClaim}/draft`, {
        headers: headers(),
        data: {
          ...draft,
          title: "Withdrawable travel draft",
          expectedVersion: null,
          lines: draft.lines.map((item) => ({ ...item, id: randomUUID() })),
          reason: "Prepare a separate travel claim",
        },
      })
    ).status(),
  ).toBe(200);
  const withdrawnSubmission = await confirmExpenseClaimAction(page, {
    company,
    claimId: withdrawnClaim,
    action: "submit",
    expectedVersion: 0,
    reason: "Submit travel for approval",
  });
  if (!withdrawnSubmission) throw new Error("A current submission is required for withdrawal");
  await confirmExpenseClaimAction(page, {
    company,
    claimId: withdrawnClaim,
    submissionId: withdrawnSubmission,
    action: "withdraw",
    expectedVersion: 1,
    reason: "Travel was postponed",
    loseResponse: true,
  });
  await confirmExpenseClaimAction(page, {
    company,
    claimId: withdrawnClaim,
    action: "cancel",
    expectedVersion: 2,
    reason: "Travel is no longer required",
    loseResponse: true,
  });
  const cancelled = await context.request.get(`${base}/expenses/claims/${withdrawnClaim}/review`);
  expect(cancelled.status()).toBe(200);
  const cancelledReview = await cancelled.json();
  expect(cancelledReview).toMatchObject({
    claim: {
      version: 3,
      status: "CANCELLED",
      submissionCount: 1,
      latestSubmissionId: withdrawnSubmission,
    },
    history: {
      items: [
        { version: 3, kind: "CANCELLED" },
        { version: 2, kind: "WITHDRAWN" },
        { version: 1, kind: "SUBMITTED" },
        { version: 0, kind: "DRAFT_SAVED" },
      ],
    },
  });
  expect(cancelledReview.history.items).toHaveLength(4);
  const cancelledApproval = await context.request.get(
    `${base}/expenses/submissions/${withdrawnSubmission}`,
  );
  expect(cancelledApproval.status()).toBe(200);
  expect(await cancelledApproval.json()).toMatchObject({
    approval: { status: "CANCELLED" },
    availableActions: [],
  });
  await page.goto(detail);
  await page.getByRole("tab", { name: "Payments", exact: true }).click();
  await expect(
    page.getByText("No payment instructions have been prepared.", { exact: true }),
  ).toBeVisible();
  await page.getByRole("tab", { name: "Overview", exact: true }).click();
  await expect(page.getByRole("region", { name: "Current claim", exact: true })).toContainText(
    "Approved",
  );
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await expect(page.getByRole("button", { name: "Theme", exact: true })).toHaveText("Light");
  await page.setViewportSize({ width: 760, height: 900 });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../../.work/dashboard-expense-claims-real-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
});
