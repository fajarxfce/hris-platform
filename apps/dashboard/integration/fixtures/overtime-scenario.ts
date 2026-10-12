import { randomUUID } from "node:crypto";
import { type BrowserContext, expect } from "@playwright/test";
import { companyDate } from "../../src/core/presentation/dates/company-date";

export const overtimeReviewer = "a0000000-0000-4000-8000-000000000025";
export const overtimeEmployee = "a0000000-0000-4000-8000-000000000026";

/** Prepares employment, calendar and approval policy; the request is created through the UI. */
export async function createOvertimeScenario(context: BrowserContext, company: string) {
  const csrf = (await (await context.request.get("/api/v1/auth/csrf")).json()) as {
    headerName: string;
    token: string;
  };
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const base = `/api/v1/companies/${company}`,
    employee = randomUUID(),
    shift = randomUUID(),
    holiday = randomUUID();
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
  const planPath = `${base}/workforce/overtime`;
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
    holiday,
    planPath,
    headers,
    actual,
  };
}
