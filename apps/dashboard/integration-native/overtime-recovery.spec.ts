import { randomUUID } from "node:crypto";
import { existsSync, unlinkSync } from "node:fs";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import {
  createOvertimeScenario,
  overtimeEmployee,
} from "../integration/fixtures/overtime-scenario";
import { overtimeDetailsDto } from "../src/features/workforce/data/models/overtime-details-dto";
import { overtimePageDto } from "../src/features/workforce/data/models/overtime-request-dto";
import { overtimeFixtureJson, overtimeProxy } from "./fixtures/overtime-proxy";

const prefix = "../../.work/overtime-native";
test("owned Android overtime pending-command recovery fixture", async ({
  page,
  context,
  request,
}) => {
  for (const suffix of ["ready.json", "recover", "done"])
    if (existsSync(`${prefix}-${suffix}`))
      throw new Error("Previous overtime native fixture requires review");
  const proxy = await overtimeProxy(prefix);
  try {
    const { company, csrf } = await createAdminCompany(
      page,
      context,
      "browser-overtime-admin@example.invalid",
      "NATIVE_OVERTIME",
      "Overtime Command Acceptance",
    );
    const source = await createOvertimeScenario(context, company);
    const base = `/api/v1/companies/${company}`;
    const grant = await context.request.put(`${base}/members/${overtimeEmployee}`, {
      headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() },
      data: {
        expectedVersion: 0,
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
        reason: "Owned native pending-summary acceptance",
      },
    });
    expect(grant.status()).toBe(200);
    const login = await request.post("/api/v1/auth/native/login", {
      headers: { "Idempotency-Key": randomUUID() },
      data: {
        email: "browser-overtime-employee@example.invalid",
        password: "Browser-fixture-password-123!",
        deviceName: "Independent overtime acceptance observer",
      },
    });
    expect(login.status()).toBe(200);
    const token = (await login.json()).credentials.accessToken;
    const memberHeaders = {
      Authorization: `Bearer ${token}`,
      "X-HRIS-Client-Platform": "ANDROID",
      "X-HRIS-Client-Build": "1",
    };
    const path = `${base}/workforce/overtime?employeeId=${source.employee}&from=${source.workDate}&until=${source.workDate}&limit=50`;
    const empty = await request.get(path, { headers: memberHeaders });
    expect(empty.status()).toBe(200);
    expect(overtimePageDto.parse(await empty.json()).items).toHaveLength(0);
    overtimeFixtureJson(`${prefix}-ready.json`, {
      company,
      employee: source.employee,
      account: overtimeEmployee,
      email: "browser-overtime-employee@example.invalid",
      workDate: source.workDate,
      start: "08:00",
      end: "11:00",
      breakMinutes: 15,
      reason: "Retain one overtime plan after committed response loss",
      proxyPort: 18082,
    });
    console.log("Owned Android overtime pending-command fixture ready");
    await expect
      .poll(() => existsSync(`${prefix}-done`), { timeout: 720_000, intervals: [1000] })
      .toBe(true);
    const response = await request.get(path, { headers: memberHeaders });
    expect(response.status()).toBe(200);
    const plans = overtimePageDto.parse(await response.json());
    expect(plans.items).toHaveLength(1);
    const plan = plans.items[0];
    if (!plan) throw new Error("One retained native plan required");
    expect(plan).toMatchObject({
      employeeId: source.employee,
      authorId: overtimeEmployee,
      version: 0,
      status: "PLANNED",
      actual: null,
      workDate: source.workDate,
      reason: "Retain one overtime plan after committed response loss",
      requested: {
        startsAt: `${source.workDate}T01:00:00Z`,
        endsAt: `${source.workDate}T04:00:00Z`,
        breakMinutes: 15,
        workedMinutes: 165,
      },
    });
    const detail = await request.get(`${base}/workforce/overtime/${plan.id}`, {
      headers: memberHeaders,
    });
    expect(detail.status()).toBe(200);
    expect(overtimeDetailsDto.parse(await detail.json()).history.items).toHaveLength(1);
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
        (event) => event.method === "POST" && event.path.endsWith("/workforce/overtime"),
      ),
    ).toBe(true);
    overtimeFixtureJson(`${prefix}-server-result.json`, {
      responseDropped: true,
      nativeCompanyWrites: 2,
      sameOperationAndInput: true,
      plans: 1,
      serverVersion: plan.version,
      historyEntries: 1,
      workedMinutes: plan.requested.workedMinutes,
      actual: false,
      requests: observed.count,
    });
  } finally {
    await proxy.close();
    if (existsSync(`${prefix}-ready.json`)) unlinkSync(`${prefix}-ready.json`);
  }
});
