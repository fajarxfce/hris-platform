import { randomUUID } from "node:crypto";
import { existsSync, unlinkSync } from "node:fs";
import { expect, test } from "@playwright/test";
import { z } from "zod";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import { companyDate } from "../src/core/presentation/dates/company-date";
import { attendanceDayDto } from "../src/features/workforce/data/models/attendance-dto";
import { attendanceFixtureJson, attendanceProxy } from "./fixtures/attendance-proxy";

const prefix = "../../.work/attendance-native";
test("owned Android attendance delivery recovery preserves independent evidence review", async ({
  page,
  context,
  request,
}) => {
  for (const suffix of ["ready.json", "recover", "done"])
    if (existsSync(`${prefix}-${suffix}`))
      throw new Error("Previous attendance native fixture requires review");
  const proxy = await attendanceProxy(prefix);
  try {
    const { company, csrf } = await createAdminCompany(
      page,
      context,
      "browser-attendance-admin@example.invalid",
      "NATIVE_ATTENDANCE",
      "Attendance Capture Acceptance",
    );
    const base = `/api/v1/companies/${company}`,
      employee = randomUUID(),
      shift = randomUUID();
    const member = "a0000000-0000-4000-8000-000000000015";
    const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
    const granted = await context.request.put(`${base}/members/${member}`, {
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
        reason: "Owned native attendance fixture",
      },
    });
    expect(granted.status()).toBe(200);
    const created = await context.request.post(`${base}/employees`, {
      headers: headers(),
      data: {
        id: employee,
        employeeNumber: "NATIVE-ATTENDANCE-001",
        person: {
          id: randomUUID(),
          accountId: member,
          legalName: "Native Attendance Employee",
          nationality: "ID",
        },
        terms: {
          effectiveFrom: "2025-01-01",
          startDate: "2025-01-01",
          status: "ACTIVE",
          contract: "PERMANENT",
        },
        reason: "Owned native attendance employment",
      },
    });
    expect(created.status()).toBe(200);
    const policy = await context.request.put(`${base}/workforce/shifts/${shift}`, {
      headers: headers(),
      data: {
        code: "NATIVE_DAY",
        name: "Native day shift",
        startsAt: "09:00",
        endsAt: "17:00",
        breakMinutes: 30,
        timezone: "Asia/Jakarta",
        mode: "REMOTE",
        locationRequired: false,
        maxAccuracyMeters: 100,
        fence: null,
        reason: "Fictional native schedule",
      },
    });
    expect(policy.status()).toBe(200);
    const date = companyDate("Asia/Jakarta", new Date());
    const employeeBase = `${base}/workforce/employees/${employee}`;
    const roster = await context.request.put(`${employeeBase}/roster/${date}`, {
      headers: headers(),
      data: { shift: { id: shift, version: 0 }, reason: "Owned native attendance day" },
    });
    expect(roster.status()).toBe(200);
    const login = await request.post("/api/v1/auth/native/login", {
      headers: { "Idempotency-Key": randomUUID() },
      data: {
        email: "browser-attendance-employee@example.invalid",
        password: "Browser-fixture-password-123!",
        deviceName: "Independent native attendance observer",
      },
    });
    expect(login.status()).toBe(200);
    const token = (await login.json()).credentials.accessToken;
    const memberHeaders = {
      Authorization: `Bearer ${token}`,
      "X-HRIS-Client-Platform": "ANDROID",
      "X-HRIS-Client-Build": "1",
    };
    const path = `${employeeBase}/attendance?from=${date}&until=${date}`;
    const empty = await request.get(path, { headers: memberHeaders });
    expect(empty.status()).toBe(200);
    expect(
      z
        .array(attendanceDayDto)
        .max(1)
        .parse(await empty.json())
        .flatMap((day) => day.entries),
    ).toEqual([]);
    const readyAt = Date.now();
    attendanceFixtureJson(`${prefix}-ready.json`, {
      company,
      employee,
      account: member,
      email: "browser-attendance-employee@example.invalid",
      date,
      kind: "CLOCK_IN",
      withoutLocation: true,
      proxyPort: 18082,
    });
    console.log("Owned Android attendance capture fixture ready");
    await expect
      .poll(() => existsSync(`${prefix}-done`), { timeout: 720_000, intervals: [1000] })
      .toBe(true);
    const response = await request.get(path, { headers: memberHeaders });
    expect(response.status()).toBe(200);
    const days = z
      .array(attendanceDayDto)
      .length(1)
      .parse(await response.json());
    const day = days[0];
    if (!day) throw new Error("One native capture day required");
    expect(day).toMatchObject({
      workDate: date,
      pendingCount: 1,
      acceptedMinutes: 0,
      correction: null,
    });
    expect(day.entries).toHaveLength(1);
    const entry = day.entries[0];
    if (!entry) throw new Error("One native capture required");
    expect(entry).toMatchObject({
      kind: "CLOCK_IN",
      offline: true,
      location: null,
      status: "PENDING",
      initialStatus: "PENDING",
      version: 0,
      review: null,
      schedule: {
        workDate: date,
        kind: "WORK",
        origin: "ROSTER",
        originVersion: 0,
        plannedMinutes: 450,
        shift: { id: shift, revision: 0 },
      },
    });
    expect(entry.issues).toContain("OFFLINE");
    expect(Date.parse(entry.capturedAt)).toBeGreaterThanOrEqual(readyAt);
    expect(Date.parse(entry.capturedAt)).toBeLessThanOrEqual(Date.parse(entry.receivedAt));
    expect(Date.parse(entry.receivedAt)).toBeLessThanOrEqual(Date.now());
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
    expect(companyPosts.map(({ method, path }) => ({ method, path }))).toEqual([
      { method: "POST", path: "/api/v1/companies/:id/workforce/employees/:id/attendance" },
      { method: "POST", path: "/api/v1/companies/:id/workforce/employees/:id/attendance" },
    ]);
    attendanceFixtureJson(`${prefix}-server-result.json`, {
      responseDropped: true,
      nativeCompanyWrites: 2,
      sameOperationAndInput: true,
      entries: 1,
      serverVersion: entry.version,
      offline: entry.offline,
      location: entry.location,
      review: entry.review,
      status: entry.status,
      pendingCount: day.pendingCount,
      acceptedMinutes: day.acceptedMinutes,
      originalCapturedAt: entry.capturedAt,
      originalReceivedAt: entry.receivedAt,
      issues: entry.issues,
      requests: observed.count,
    });
  } finally {
    await proxy.close();
    if (existsSync(`${prefix}-ready.json`)) unlinkSync(`${prefix}-ready.json`);
  }
});
