import { randomUUID } from "node:crypto";
import { existsSync, unlinkSync } from "node:fs";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import { createLeaveScenario } from "../integration/fixtures/leave-scenario";
import { leaveBalanceDto } from "../src/features/leave/data/models/leave-balance-dto";
import { leaveRequestDetailsDto } from "../src/features/leave/data/models/leave-request-details-dto";
import { leaveRequestPageDto } from "../src/features/leave/data/models/leave-request-summary-dto";
import { leaveFixtureJson, leaveProxy } from "./fixtures/leave-proxy";

const prefix = "../../.work/leave-native";
test("owned Android leave submission recovery and single balance reservation fixture", async ({
  page,
  context,
  request,
}) => {
  for (const suffix of ["ready.json", "recover", "done"])
    if (existsSync(`${prefix}-${suffix}`))
      throw new Error("Previous leave native fixture requires review");
  const proxy = await leaveProxy(prefix);
  try {
    const { company, csrf } = await createAdminCompany(
      page,
      context,
      "browser-leave-creation-admin@example.invalid",
      "NATIVE_LEAVE",
      "Leave Command Acceptance",
    );
    const base = `/api/v1/companies/${company}`;
    const member = "a0000000-0000-4000-8000-000000000008";
    const reviewer = "a0000000-0000-4000-8000-000000000037";
    const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
    for (const [account, permissions] of [
      [reviewer, ["leave.approve"]],
      [
        member,
        [
          "company.read",
          "people.self.read",
          "announcements.read",
          "leave.self.manage",
          "attendance.self.record",
          "overtime.self.manage",
          "expenses.self.manage",
          "documents.self.read",
        ],
      ],
    ] as const) {
      const grant = await context.request.put(`${base}/members/${account}`, {
        headers: headers(),
        data: { active: true, permissions, reason: "Owned native leave fixture" },
      });
      expect(grant.status()).toBe(200);
    }
    const source = await createLeaveScenario(context, company, reviewer, { submit: false });
    const binding = await context.request.post(
      `${base}/employees/${source.employee}/account-link`,
      {
        headers: headers(),
        data: { accountId: member, expectedVersion: 0, reason: "Owned native leave employee" },
      },
    );
    expect(binding.status()).toBe(200);
    const login = await request.post("/api/v1/auth/native/login", {
      headers: { "Idempotency-Key": randomUUID() },
      data: {
        email: "browser-binding-employee@example.invalid",
        password: "Browser-fixture-password-123!",
        deviceName: "Independent native leave observer",
      },
    });
    expect(login.status()).toBe(200);
    const token = (await login.json()).credentials.accessToken;
    const memberHeaders = {
      Authorization: `Bearer ${token}`,
      "X-HRIS-Client-Platform": "ANDROID",
      "X-HRIS-Client-Build": "1",
    };
    const path = `${base}/leave/requests?employeeId=${source.employee}&from=${source.date}&until=${source.date}&limit=20`;
    const empty = await request.get(path, { headers: memberHeaders });
    expect(empty.status()).toBe(200);
    expect(leaveRequestPageDto.parse(await empty.json()).items).toHaveLength(0);
    const balancePath = `${base}/leave/employees/${source.employee}/balances/${source.type}/${source.date.slice(0, 4)}`;
    const opening = await context.request.get(balancePath);
    expect(opening.status()).toBe(200);
    const openingBalance = leaveBalanceDto.parse((await opening.json()).balance);
    expect(openingBalance).toMatchObject({
      availableDays: "2",
      reservedDays: "0",
      consumedDays: "0",
    });
    leaveFixtureJson(`${prefix}-ready.json`, {
      company,
      employee: source.employee,
      type: source.type,
      account: member,
      email: "browser-binding-employee@example.invalid",
      date: source.date,
      portion: "FIRST_HALF",
      chargedDays: "0.5",
      reason: "Retain one leave request after committed response loss",
      proxyPort: 18082,
    });
    console.log("Owned Android leave submission fixture ready");
    await expect
      .poll(() => existsSync(`${prefix}-done`), { timeout: 720_000, intervals: [1000] })
      .toBe(true);
    const response = await request.get(path, { headers: memberHeaders });
    expect(response.status()).toBe(200);
    const requests = leaveRequestPageDto.parse(await response.json());
    expect(requests.items).toHaveLength(1);
    const submitted = requests.items[0];
    if (!submitted) throw new Error("One native leave request required");
    expect(submitted).toMatchObject({
      employeeId: source.employee,
      status: "PENDING",
      version: 0,
      chargedDays: "0.5",
      from: source.date,
      until: source.date,
    });
    const detail = await request.get(`${base}/leave/requests/${submitted.id}?historyLimit=20`, {
      headers: memberHeaders,
    });
    expect(detail.status()).toBe(200);
    const saved = leaveRequestDetailsDto.parse(await detail.json());
    expect(saved).toMatchObject({
      ownerAccountId: member,
      authorId: member,
      version: 0,
      cancellation: null,
      attachments: [],
      reason: "Retain one leave request after committed response loss",
      policy: { typeId: source.type, revision: 0 },
      approval: {
        status: "PENDING",
        authorId: member,
        requesterId: member,
        currentStep: 0,
        version: 0,
        stages: [[reviewer]],
      },
    });
    expect(saved.days).toHaveLength(1);
    expect(saved.days[0]).toMatchObject({
      workDate: source.date,
      portion: "FIRST_HALF",
      chargedDays: "0.5",
      plannedMinutes: 450,
      chargedMinutes: 225,
    });
    expect(saved.history.items).toHaveLength(1);
    const after = await context.request.get(balancePath);
    expect(after.status()).toBe(200);
    const balance = leaveBalanceDto.parse((await after.json()).balance);
    expect(balance).toMatchObject({
      availableDays: "1.5",
      reservedDays: "0.5",
      consumedDays: "0",
      version: openingBalance.version + 1,
    });
    const observed = proxy.snapshot();
    expect(observed.interrupted).toBe(true);
    expect(observed.mutations).toHaveLength(2);
    expect(observed.mutations[0]).toEqual(observed.mutations[1]);
    expect(observed.mutations[0]?.status).toBe(200);
    const companyPosts = observed.events.filter(
      (event) =>
        event.forwarded &&
        !["GET", "HEAD"].includes(event.method) &&
        event.path.startsWith("/api/v1/companies/"),
    );
    // Preview is an intentional read-only POST, not a business command.
    expect(companyPosts.map(({ method, path }) => ({ method, path }))).toEqual([
      { method: "POST", path: "/api/v1/companies/:id/leave/me/requests/preview" },
      { method: "POST", path: "/api/v1/companies/:id/leave/requests" },
      { method: "POST", path: "/api/v1/companies/:id/leave/requests" },
    ]);
    leaveFixtureJson(`${prefix}-server-result.json`, {
      responseDropped: true,
      nativeCompanyWrites: 2,
      nativePreviewPosts: 1,
      sameOperationAndInput: true,
      leaveRequests: 1,
      serverVersion: saved.version,
      historyEntries: 1,
      approvalStages: 1,
      chargedDays: saved.chargedDays,
      availableDays: balance.availableDays,
      reservedDays: balance.reservedDays,
      consumedDays: balance.consumedDays,
      balanceVersionIncrement: balance.version - openingBalance.version,
      requests: observed.count,
    });
  } finally {
    await proxy.close();
    if (existsSync(`${prefix}-ready.json`)) unlinkSync(`${prefix}-ready.json`);
  }
});
