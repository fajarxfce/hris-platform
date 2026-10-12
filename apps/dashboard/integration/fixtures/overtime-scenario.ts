import { randomUUID } from "node:crypto";
import { type BrowserContext, expect } from "@playwright/test";
import { companyDate } from "../../src/core/presentation/dates/company-date";
import type { OvertimeDetailsDto } from "../../src/features/workforce/data/models/overtime-details-dto";

export const overtimeReviewer = "a0000000-0000-4000-8000-000000000025";
export const overtimeEmployee = "a0000000-0000-4000-8000-000000000026";

/** Prepares a real plan and later calendar change; actual time and decisions are entered through the UI. */
export async function createOvertimeScenario(context: BrowserContext, company: string) {
  const csrf = (await (await context.request.get("/api/v1/auth/csrf")).json()) as {
    headerName: string;
    token: string;
  };
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const base = `/api/v1/companies/${company}`,
    employee = randomUUID(),
    shift = randomUUID(),
    holiday = randomUUID(),
    id = randomUUID();
  for (const [account, permissions] of [
    [overtimeReviewer, ["company.read", "overtime.approve", "approvals.read"]],
    [overtimeEmployee, ["overtime.self.manage"]],
  ] as const) {
    const saved = await context.request.put(`${base}/members/${account}`, {
      headers: headers(),
      data: { active: true, permissions, reason: "Overtime scenario access" },
    });
    expect(saved.status()).toBe(200);
  }
  const created = await context.request.post(`${base}/employees`, {
    headers: headers(),
    data: {
      id: employee,
      employeeNumber: "OVERTIME-001",
      person: {
        id: randomUUID(),
        accountId: overtimeEmployee,
        legalName: "Overtime Fixture Employee",
        nationality: "ID",
      },
      terms: {
        effectiveFrom: "2025-01-01",
        startDate: "2025-01-01",
        status: "ACTIVE",
        contract: "PERMANENT",
      },
      reason: "Overtime scenario",
    },
  });
  expect(created.status()).toBe(200);
  const day = new Date(`${companyDate("Asia/Jakarta", new Date())}T00:00:00Z`);
  day.setUTCDate(day.getUTCDate() - 1);
  const workDate = day.toISOString().slice(0, 10);
  const weekday =
    ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"][
      day.getUTCDay()
    ] ?? "MONDAY";
  expect(
    (
      await context.request.put(`${base}/workforce/shifts/${shift}`, {
        headers: headers(),
        data: {
          code: "OVERTIME_DAY",
          name: "Ordinary daytime",
          startsAt: "08:00",
          endsAt: "17:00",
          breakMinutes: 60,
          timezone: "Asia/Jakarta",
          mode: "REMOTE",
          reason: "Overtime scenario",
        },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await context.request.put(`${base}/workforce/employees/${employee}/schedule`, {
        headers: headers(),
        data: {
          effectiveFrom: "2025-01-01",
          days: { [weekday]: { id: shift, version: 0 } },
          reason: "Ordinary work week",
        },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await context.request.put(`${base}/workforce/holidays/${holiday}`, {
        headers: headers(),
        data: { workDate, name: "Inventory holiday", reason: "Original calendar" },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await context.request.put(`${base}/approvals/templates/${randomUUID()}`, {
        headers: headers(),
        data: {
          name: "Independent overtime review",
          kind: "OVERTIME",
          effectiveFrom: "2025-01-01",
          stages: [{ assignment: "NAMED", accountIds: [overtimeReviewer] }],
          reason: "Overtime review routing",
        },
      })
    ).status(),
  ).toBe(200);
  const overtimePath = `${base}/workforce/overtime/${id}`,
    planPath = `${base}/workforce/overtime`;
  const planKey = randomUUID(),
    plan = {
      id,
      employeeId: employee,
      expectedEmploymentVersion: 0,
      workDate,
      requested: {
        startsAt: `${workDate}T01:00:00Z`,
        endsAt: `${workDate}T04:00:00Z`,
        breakMinutes: 0,
      },
      reason: "Inventory count on the original day off",
    };
  const planned = await context.request.post(planPath, {
    headers: { ...headers(), "Idempotency-Key": planKey },
    data: plan,
  });
  expect(planned.status()).toBe(200);
  const plannedReceipt: unknown = await planned.json();
  const before = await context.request.get(overtimePath);
  expect(before.status()).toBe(200);
  expect(((await before.json()) as OvertimeDetailsDto).request.schedule).toMatchObject({
    kind: "OFF",
    origin: "HOLIDAY",
    holidayId: holiday,
  });
  expect(
    (
      await context.request.put(`${base}/workforce/holidays/${holiday}`, {
        headers: headers(),
        data: {
          workDate,
          name: "Inventory holiday",
          expectedVersion: 0,
          active: false,
          reason: "Later calendar change",
        },
      })
    ).status(),
  ).toBe(200);
  const actual = {
    expectedVersion: 0,
    actual: {
      startsAt: `${workDate}T01:15:00Z`,
      endsAt: `${workDate}T03:45:00Z`,
      breakMinutes: 15,
    },
    reason: "Inventory completed",
  };
  return {
    base,
    employee,
    workDate,
    id,
    holiday,
    planPath,
    overtimePath,
    headers,
    plan,
    planKey,
    actual,
    plannedReceipt,
  };
}
