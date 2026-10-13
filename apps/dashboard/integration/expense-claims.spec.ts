import { randomUUID } from "node:crypto";
import { expect, type Page, test } from "@playwright/test";
import { companyDate } from "../src/core/presentation/dates/company-date";
import { createAdminCompany } from "./fixtures/admin-company";
import { authenticatorCode } from "./fixtures/authenticator";

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

test("real expense review preserves submitted evidence, replays a lost decision and supports correction and rejection", async ({
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
    category = randomUUID(),
    claim = randomUUID(),
    line = randomUUID(),
    submission = randomUUID();
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
  const draft = {
    employmentId: employee,
    title: "Customer visit",
    description: "Transport to customer office",
    lines: [
      {
        id: line,
        categoryId: category,
        occurredOn: today,
        amount: "150000.75",
        description: "Return train ticket",
        costCenterId: null,
        receiptRevisionIds: [],
      },
    ],
    reason: "Expense details confirmed",
  };
  const saved = await context.request.put(`${base}/expenses/claims/${claim}/draft`, {
    headers: headers(),
    data: { ...draft, expectedVersion: null },
  });
  expect(saved.status()).toBe(200);
  const revised = await context.request.put(`${base}/expenses/claims/${claim}/draft`, {
    headers: headers(),
    data: { ...draft, title: "Customer visit reimbursement", expectedVersion: 0 },
  });
  expect(revised.status()).toBe(200);
  const submitted = await context.request.post(`${base}/expenses/claims/${claim}/submit`, {
    headers: headers(),
    data: { submissionId: submission, expectedVersion: 1, reason: "Submit travel reimbursement" },
  });
  expect(submitted.status()).toBe(200);
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

    const correctedClaim = randomUUID(),
      firstSubmission = randomUUID(),
      correctedSubmission = randomUUID();
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
    expect(
      (
        await context.request.post(`${base}/expenses/claims/${correctedClaim}/submit`, {
          headers: headers(),
          data: {
            submissionId: firstSubmission,
            expectedVersion: 0,
            reason: "Submit expense for review",
          },
        })
      ).status(),
    ).toBe(200);
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
    expect(
      (
        await context.request.put(`${base}/expenses/claims/${correctedClaim}/draft`, {
          headers: headers(),
          data: { ...correctionDraft, description: "Updated item description", expectedVersion: 2 },
        })
      ).status(),
    ).toBe(200);
    expect(
      (
        await context.request.post(`${base}/expenses/claims/${correctedClaim}/submit`, {
          headers: headers(),
          data: {
            submissionId: correctedSubmission,
            expectedVersion: 3,
            reason: "Resubmit corrected expense",
          },
        })
      ).status(),
    ).toBe(200);
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
