import { randomUUID } from "node:crypto";
import { type BrowserContext, expect, type Page } from "@playwright/test";
import { companyDate } from "../src/core/presentation/dates/company-date";

/** Creates a real expense submission; no database shortcut constructs the approval under test. */
export async function reviewApprovalReassignment(
  page: Page,
  context: BrowserContext,
  company: string,
  delegate: string,
) {
  const csrf = (await (await context.request.get("/api/v1/auth/csrf")).json()) as {
    headerName: string;
    token: string;
  };
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const base = `/api/v1/companies/${company}`;
  const employee = randomUUID();
  const category = randomUUID();
  const template = randomUUID();
  const claim = randomUUID();
  const submission = randomUUID();
  const created = await context.request.post(`${base}/employees`, {
    headers: headers(),
    data: {
      id: employee,
      employeeNumber: "APPROVAL-EMPLOYEE",
      person: { id: randomUUID(), legalName: "Browser Employee", nationality: "ID" },
      terms: {
        effectiveFrom: "2025-01-01",
        startDate: "2025-01-01",
        status: "ACTIVE",
        contract: "PERMANENT",
      },
      reason: "Browser approval setup",
    },
  });
  expect(created.status()).toBe(200);
  const policy = await context.request.put(`${base}/expenses/categories/${category}`, {
    headers: headers(),
    data: {
      code: "BROWSER",
      name: "Browser expenses",
      effectiveFrom: "2025-01-01",
      maximumLineAmount: "50000.00",
      maximumClaimAmount: "100000.00",
      receiptRequired: false,
      costCenterRequired: false,
      maximumAgeDays: 30,
      reason: "Browser expense policy",
    },
  });
  expect(policy.status()).toBe(200);
  const session = (await (await context.request.get("/api/v1/me")).json()) as {
    account: { id: string };
  };
  const rules = await context.request.put(`${base}/approvals/templates/${template}`, {
    headers: headers(),
    data: {
      name: "Browser expense review",
      kind: "EXPENSE",
      effectiveFrom: "2025-01-01",
      category: "BROWSER",
      minimumAmount: "0",
      stages: [{ assignment: "NAMED", accountIds: [session.account.id] }],
      reason: "Exercise blocked maker assignment",
    },
  });
  expect(rules.status()).toBe(200);
  const draft = await context.request.put(`${base}/expenses/claims/${claim}/draft`, {
    headers: headers(),
    data: {
      employmentId: employee,
      title: "Browser reimbursement",
      lines: [
        {
          id: randomUUID(),
          categoryId: category,
          occurredOn: companyDate("Asia/Jakarta", new Date()),
          amount: "10000.00",
          description: "Fixture expense",
        },
      ],
      reason: "Browser draft",
    },
  });
  expect(draft.status()).toBe(200);
  const submitted = await context.request.post(`${base}/expenses/claims/${claim}/submit`, {
    headers: headers(),
    data: { submissionId: submission, expectedVersion: 0, reason: "Browser submission" },
  });
  expect(submitted.status()).toBe(200);
  const source = await context.request.get(`${base}/expenses/submissions/${submission}`);
  expect(source.status()).toBe(200);
  const before = (await source.json()) as {
    approval: { id: string; version: number; status: string };
    claimVersion: number;
    claimStatus: string;
  };
  expect(before.approval).toMatchObject({ version: 0, status: "BLOCKED" });
  await page.getByRole("link", { name: "Approvals", exact: true }).click();
  await page
    .getByRole("table", { name: "Approval inbox", exact: true })
    .getByRole("button", { name: new RegExp(`${submission}$`, "u") })
    .click();
  await page.getByRole("link", { name: "Reassign approvers", exact: true }).click();
  await page.getByRole("button", { name: "Add approver", exact: true }).click();
  const picker = page.getByRole("dialog", { name: "Select approver", exact: true });
  await expect(
    picker.getByRole("button", { name: "Select: Browser Approval Admin", exact: true }),
  ).toHaveCount(0);
  await picker.getByRole("button", { name: "Select: Browser Delegate", exact: true }).click();
  await page.getByLabel("Reason", { exact: true }).fill("Independent expense reviewer");
  const writes: { key: string | undefined; body: unknown }[] = [];
  await page.route("**/api/v1/companies/*/approvals/*/reassign", async (route) => {
    writes.push({
      key: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    if (writes.length === 1) return route.abort("failed");
    return route.fulfill({ response });
  });
  await page.getByRole("button", { name: "Confirm reassignment", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result is unconfirmed");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Approvers updated.");
  await page.unroute("**/api/v1/companies/*/approvals/*/reassign");
  expect(writes).toHaveLength(2);
  expect(writes[0]).toEqual(writes[1]);
  expect(writes[0]?.body).toEqual({
    version: 0,
    assignees: [delegate],
    reason: "Independent expense reviewer",
  });
  const updated = await context.request.get(`${base}/approvals/${before.approval.id}`);
  expect(await updated.json()).toMatchObject({
    version: 1,
    status: "PENDING",
    currentStep: 0,
    stages: [{ assignees: [delegate] }],
  });
  const claimState = await context.request.get(`${base}/expenses/submissions/${submission}`);
  expect(await claimState.json()).toMatchObject({
    claimVersion: before.claimVersion,
    claimStatus: before.claimStatus,
    approval: { version: 1, status: "PENDING" },
  });
  await page.getByRole("link", { name: "View request", exact: true }).click();
  await expect(page.getByRole("table", { name: "Approval stages", exact: true })).toContainText(
    delegate,
  );
}
