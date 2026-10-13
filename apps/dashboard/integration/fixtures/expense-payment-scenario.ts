import { randomUUID } from "node:crypto";
import { type BrowserContext, expect, type Page } from "@playwright/test";
import { companyDate } from "../../src/core/presentation/dates/company-date";
import { authenticatorCode } from "./authenticator";
export const paymentAccounts = {
  reviewer: "a0000000-0000-4000-8000-000000000033",
  maker: "a0000000-0000-4000-8000-000000000034",
  checker: "a0000000-0000-4000-8000-000000000035",
};
export type PaymentCsrf = Readonly<{ headerName: string; token: string }>;
export const paymentHeaders = (csrf: PaymentCsrf) => ({
  [csrf.headerName]: csrf.token,
  "Idempotency-Key": randomUUID(),
});
export async function paymentSession(
  page: Page,
  role: keyof typeof paymentAccounts,
): Promise<PaymentCsrf> {
  await page.goto("/");
  await page
    .getByLabel("Email", { exact: true })
    .fill(`browser-expense-payment-${role}@example.invalid`);
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
  await expect(page.getByRole("combobox", { name: "Company", exact: true })).toBeVisible();
  return (await (await page.request.get("/api/v1/auth/csrf")).json()) as PaymentCsrf;
}
export async function prepareExpensePaymentClaims(
  context: BrowserContext,
  company: string,
  csrf: PaymentCsrf,
) {
  const base = `/api/v1/companies/${company}`,
    today = companyDate("Asia/Jakarta", new Date()),
    category = randomUUID();
  for (const [role, account] of Object.entries(paymentAccounts)) {
    const response = await context.request.put(`${base}/members/${account}`, {
      headers: paymentHeaders(csrf),
      data: {
        active: true,
        permissions: ["company.read", role === "reviewer" ? "expenses.approve" : "expenses.pay"],
        reason: "Separate finance responsibilities for browser acceptance",
      },
    });
    expect(response.status()).toBe(200);
  }
  const policy = await context.request.put(`${base}/expenses/categories/${category}`, {
    headers: paymentHeaders(csrf),
    data: {
      code: "PAYMENT_TRAVEL",
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
      reason: "Finance browser acceptance",
    },
  });
  expect(policy.status()).toBe(200);
  const template = await context.request.put(`${base}/approvals/templates/${randomUUID()}`, {
    headers: paymentHeaders(csrf),
    data: {
      name: "Finance fixture expense review",
      kind: "EXPENSE",
      effectiveFrom: "2025-01-01",
      stages: [{ assignment: "NAMED", accountIds: [paymentAccounts.reviewer] }],
      reason: "Independent expense approval",
    },
  });
  expect(template.status()).toBe(200);
  const items: {
    id: string;
    claimId: string;
    submissionId: string;
    name: string;
    amount: string;
    destination: { bankCode: string; accountNumber: string; accountName: string };
  }[] = [];
  for (const [index, amount] of ["150000.75", "275000.25"].entries()) {
    const employee = randomUUID(),
      name = `Payment Employee ${index + 1}`,
      claimId = randomUUID(),
      submissionId = randomUUID();
    const created = await context.request.post(`${base}/employees`, {
      headers: paymentHeaders(csrf),
      data: {
        id: employee,
        employeeNumber: `PAY-${index + 1}`,
        person: { id: randomUUID(), legalName: name, nationality: "ID" },
        terms: {
          effectiveFrom: "2025-01-01",
          startDate: "2025-01-01",
          status: "ACTIVE",
          contract: "PERMANENT",
        },
        reason: "Fictional payment employee",
      },
    });
    expect(created.status()).toBe(200);
    const saved = await context.request.put(`${base}/expenses/claims/${claimId}/draft`, {
      headers: paymentHeaders(csrf),
      data: {
        employmentId: employee,
        title: `Customer visit ${index + 1}`,
        description: "Travel reimbursement",
        lines: [
          {
            id: randomUUID(),
            categoryId: category,
            occurredOn: today,
            amount,
            description: "Return train ticket",
            costCenterId: null,
            receiptRevisionIds: [],
          },
        ],
        expectedVersion: null,
        reason: "Travel evidence checked",
      },
    });
    expect(saved.status()).toBe(200);
    const submitted = await context.request.post(`${base}/expenses/claims/${claimId}/submit`, {
      headers: paymentHeaders(csrf),
      data: { submissionId, expectedVersion: 0, reason: "Submit reimbursement for review" },
    });
    expect(submitted.status()).toBe(200);
    items.push({
      id: randomUUID(),
      claimId,
      submissionId,
      name,
      amount,
      destination: { bankCode: "014", accountNumber: `00123456789${index}`, accountName: name },
    });
  }
  return { base, today, items };
}
