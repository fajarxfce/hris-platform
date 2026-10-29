import { randomUUID } from "node:crypto";
import { existsSync, unlinkSync } from "node:fs";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import { companyDate } from "../src/core/presentation/dates/company-date";
import {
  expenseClaimPageDto,
  expenseClaimReviewDto,
} from "../src/features/expenses/data/models/expense-claim-dto";
import { expenseFixtureJson, expenseProxy } from "./fixtures/expense-proxy";

const prefix = "../../.work/expense-native";
test("owned Android expense save recovery and pending draft deduplication fixture", async ({
  page,
  context,
  request,
}) => {
  for (const suffix of ["ready.json", "recover", "done"])
    if (existsSync(`${prefix}-${suffix}`))
      throw new Error("Previous expense native fixture requires review");
  const proxy = await expenseProxy(prefix);
  try {
    const { company, csrf } = await createAdminCompany(
      page,
      context,
      "browser-expense-claim-admin@example.invalid",
      "NATIVE_EXPENSE",
      "Expense Save Acceptance",
    );
    const base = `/api/v1/companies/${company}`,
      employee = randomUUID(),
      category = randomUUID();
    const member = "a0000000-0000-4000-8000-000000000008";
    const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
    const grant = await context.request.put(`${base}/members/${member}`, {
      headers: headers(),
      data: {
        active: true,
        permissions: [
          "company.read",
          "people.self.read",
          "announcements.read",
          "leave.self.manage",
          "attendance.self.record",
          "overtime.self.manage",
          "expenses.self.manage",
          "documents.self.read",
        ],
        reason: "Owned native expense fixture",
      },
    });
    expect(grant.status()).toBe(200);
    const created = await context.request.post(`${base}/employees`, {
      headers: headers(),
      data: {
        id: employee,
        employeeNumber: "NATIVE-EXPENSE-001",
        person: {
          id: randomUUID(),
          accountId: member,
          legalName: "Native Expense Employee",
          nationality: "ID",
        },
        terms: {
          effectiveFrom: "2025-01-01",
          startDate: "2025-01-01",
          status: "ACTIVE",
          contract: "PERMANENT",
        },
        reason: "Owned native expense employment",
      },
    });
    expect(created.status()).toBe(200);
    const policy = await context.request.put(`${base}/expenses/categories/${category}`, {
      headers: headers(),
      data: {
        code: "NATIVE_TRAVEL",
        name: "Travel",
        effectiveFrom: "2025-01-01",
        maximumLineAmount: "500000.25",
        maximumClaimAmount: "2000000.75",
        maximumAgeDays: 30,
        allowedContracts: ["PERMANENT"],
        receiptRequired: false,
        costCenterRequired: false,
        active: true,
        expectedVersion: null,
        reason: "Reviewed fictional native travel category",
      },
    });
    expect(policy.status()).toBe(200);
    const login = await request.post("/api/v1/auth/native/login", {
      headers: { "Idempotency-Key": randomUUID() },
      data: {
        email: "browser-binding-employee@example.invalid",
        password: "Browser-fixture-password-123!",
        deviceName: "Independent native expense observer",
      },
    });
    expect(login.status()).toBe(200);
    const token = (await login.json()).credentials.accessToken;
    const memberHeaders = {
      Authorization: `Bearer ${token}`,
      "X-HRIS-Client-Platform": "ANDROID",
      "X-HRIS-Client-Build": "1",
    };
    const today = companyDate("Asia/Jakarta", new Date());
    const path = `${base}/expenses/claims?employmentId=${employee}&from=${today}&until=${today}&limit=20`;
    const empty = await request.get(path, { headers: memberHeaders });
    expect(empty.status()).toBe(200);
    expect(expenseClaimPageDto.parse(await empty.json()).items).toHaveLength(0);
    expenseFixtureJson(`${prefix}-ready.json`, {
      company,
      employee,
      category,
      account: member,
      email: "browser-binding-employee@example.invalid",
      date: today,
      title: "Native expense recovery",
      amount: "25000.25",
      lineDescription: "Local taxi",
      reason: "Retain one saved claim after committed response loss",
      proxyPort: 18082,
    });
    console.log("Owned Android expense save fixture ready");
    await expect
      .poll(() => existsSync(`${prefix}-done`), { timeout: 720_000, intervals: [1000] })
      .toBe(true);
    const response = await request.get(path, { headers: memberHeaders });
    expect(response.status()).toBe(200);
    const claims = expenseClaimPageDto.parse(await response.json());
    expect(claims.items).toHaveLength(1);
    const claim = claims.items[0];
    if (!claim) throw new Error("One native expense claim required");
    expect(claim).toMatchObject({
      employmentId: employee,
      title: "Native expense recovery",
      status: "DRAFT",
      version: 0,
      totalAmount: "25000.25",
    });
    const detail = await request.get(
      `${base}/expenses/claims/${claim.id}/review?historyLimit=20&submissionsLimit=20`,
      { headers: memberHeaders },
    );
    expect(detail.status()).toBe(200);
    const saved = expenseClaimReviewDto.parse(await detail.json());
    expect(saved.claim).toMatchObject({
      createdBy: member,
      version: 0,
      draftRevision: 0,
      submissionCount: 0,
      latestSubmissionId: null,
    });
    expect(saved.claim.draft.lines).toHaveLength(1);
    expect(saved.claim.draft.lines[0]).toMatchObject({
      categoryId: category,
      occurredOn: today,
      amount: "25000.25",
      description: "Local taxi",
      costCenterId: null,
      receiptRevisionIds: [],
    });
    expect(saved.claim.draft.reason).toBe("Retain one saved claim after committed response loss");
    expect(saved.history.items).toHaveLength(1);
    expect(saved.submissions.items).toEqual([]);
    expect(saved.payments).toEqual([]);
    const observed = proxy.snapshot();
    expect(observed.interrupted).toBe(true);
    expect(observed.mutations).toHaveLength(2);
    expect(observed.mutations[0]).toEqual(observed.mutations[1]);
    expect(observed.mutations[0]?.status).toBe(200);
    const companyWrites = observed.events.filter(
      (event) =>
        event.forwarded &&
        !["GET", "HEAD"].includes(event.method) &&
        event.path.startsWith("/api/v1/companies/"),
    );
    expect(companyWrites).toHaveLength(2);
    expect(
      companyWrites.every(
        (event) => event.method === "PUT" && event.path.endsWith("/expenses/claims/:id/draft"),
      ),
    ).toBe(true);
    expenseFixtureJson(`${prefix}-server-result.json`, {
      responseDropped: true,
      nativeCompanyWrites: 2,
      sameOperationAndInput: true,
      claims: 1,
      serverVersion: 0,
      draftRevision: 0,
      lines: 1,
      totalAmount: "25000.25",
      historyEntries: 1,
      submissions: 0,
      requests: observed.count,
    });
  } finally {
    await proxy.close();
    if (existsSync(`${prefix}-ready.json`)) unlinkSync(`${prefix}-ready.json`);
  }
});
