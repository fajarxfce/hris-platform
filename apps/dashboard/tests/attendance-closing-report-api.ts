import { expect, type Page } from "@playwright/test";
import type { AttendanceClosingCounts } from "../src/features/reporting/domain/entities/attendance-closing-report";
import type { WorkPeriodSnapshotDto } from "../src/features/workforce/data/models/work-period-snapshot-dto";
import { adjustmentDetailsDto } from "../src/features/workforce/di/attendance-adjustment-fixture";
import {
  identifier,
  workPeriodSnapshotDtoFixture,
} from "../src/features/workforce/di/work-period-fixture";
import { companyIds, installIdentityApi } from "./identity-api";

export const closingPermissions = ["company.read", "reports.read", "workforce.read"];
function facts(snapshot: WorkPeriodSnapshotDto) {
  const days = snapshot.days;
  return {
    workedDays: days.filter((d) => d.fact === "WORKED").length,
    absenceDays: days.filter((d) => d.fact === "ABSENCE_RECORDED").length,
    unrecordedDays: days.filter((d) => d.fact === "UNRECORDED").length,
    offDays: days.filter((d) => d.fact === "OFF").length,
    unassignedDays: days.filter((d) => d.fact === "UNASSIGNED").length,
    acceptedMinutes: days.reduce((sum, d) => sum + d.acceptedMinutes, 0),
  };
}
export async function installClosingReports(page: Page, permissions = closingPermissions) {
  const identity = await installIdentityApi(page, { signedIn: true, permissions });
  const original = workPeriodSnapshotDtoFixture(),
    revised = adjustmentDetailsDto(1),
    reads: string[] = [];
  const closing = {
    periodId: original.period.id,
    jobId: original.period.jobId,
    version: original.period.version,
    closedAt: original.period.closedAt,
    timezone: original.period.timezone,
  };
  const count: AttendanceClosingCounts = {
    closedMonths: 1,
    employmentMonths: 1,
    employments: 1,
    adjustedEmploymentMonths: 1,
    days: 30,
    scheduledDays: original.days.filter((d) => d.schedule.kind === "WORK").length,
    scheduledMinutes: original.days.reduce(
      (sum, d) => sum + (d.schedule.kind === "WORK" ? (d.schedule.plannedMinutes ?? 0) : 0),
      0,
    ),
    original: facts(original),
    current: facts(revised.snapshot),
  };
  const empty: AttendanceClosingCounts = {
    closedMonths: 0,
    employmentMonths: 0,
    employments: 0,
    adjustedEmploymentMonths: 0,
    days: 0,
    scheduledDays: 0,
    scheduledMinutes: 0,
    original: {
      workedDays: 0,
      absenceDays: 0,
      unrecordedDays: 0,
      offDays: 0,
      unassignedDays: 0,
      acceptedMinutes: 0,
    },
    current: {
      workedDays: 0,
      absenceDays: 0,
      unrecordedDays: 0,
      offDays: 0,
      unassignedDays: 0,
      acceptedMinutes: 0,
    },
  };
  const row = {
    employeeId: original.employeeId,
    employeeNumber: original.employee.employeeNumber,
    employeeName: original.employee.legalName,
    closing,
    adjustment: {
      id: revised.summary.id,
      revision: revised.summary.revision,
      recordedAt: revised.summary.recordedAt,
    },
    counts: count,
  };
  let fail = false,
    many = false;
  await page.route("**/api/v1/companies/*/reports/attendance-closings**", async (route) => {
    const url = new URL(route.request().url());
    reads.push(url.pathname + url.search);
    if (fail)
      return route.fulfill({
        status: 503,
        json: { code: "service_unavailable", fields: {}, parameters: {}, detail: "PRIVATE REPORT" },
      });
    const companyId = url.pathname.split("/")[4] ?? companyIds[0];
    const metadata = {
      companyId,
      evaluatedAt: "2026-10-14T00:00:00Z",
      definitionVersion: "attendance-closings.v1",
    };
    if (url.pathname.endsWith("/employees")) {
      expect(url.searchParams.get("limit")).toBe("50");
      const after = url.searchParams.get("after"),
        month = url.searchParams.get("month");
      const item = (i: number) => ({
        ...row,
        employeeId: identifier(2000 + i),
        employeeNumber: `PAGE-${i}`,
        adjustment: null,
        counts: { ...count, adjustedEmploymentMonths: 0, current: count.original },
      });
      const items = many
        ? after
          ? [item(50)]
          : Array.from({ length: 50 }, (_, i) => item(i))
        : month === "2026-09"
          ? [row]
          : [];
      const nextCursor =
        many && !after ? `attendance-closings.v1|${companyId}|${month}|${identifier(2049)}` : null;
      return route.fulfill({ json: { ...metadata, month, items, nextCursor } });
    }
    const from = url.searchParams.get("from") ?? "",
      until = url.searchParams.get("until") ?? "";
    const start = new Date(`${from}-01T00:00:00Z`),
      months = [];
    for (let i = 0; i < 12; i++) {
      const date = new Date(start);
      date.setUTCMonth(date.getUTCMonth() + i);
      const month = date.toISOString().slice(0, 7);
      if (month > until) break;
      months.push({
        month,
        closing: month === "2026-09" ? closing : null,
        counts: month === "2026-09" ? count : empty,
      });
    }
    return route.fulfill({
      json: {
        ...metadata,
        from,
        until,
        totals: from <= "2026-09" && until >= "2026-09" ? count : empty,
        months,
      },
    });
  });
  await page.route("**/api/v1/companies/*/workforce/periods/*/employees/*", (route) =>
    route.fulfill(
      route.request().url().endsWith(original.employeeId)
        ? { json: original }
        : {
            status: 404,
            json: { code: "work_period_snapshot_unavailable", fields: {}, parameters: {} },
          },
    ),
  );
  await page.route(
    `**/workforce/employees/${original.employeeId}/attendance/adjustments/${revised.summary.id}`,
    (route) => route.fulfill({ json: revised }),
  );
  return {
    identity,
    reads,
    original,
    revised,
    fail: (value: boolean) => {
      fail = value;
    },
    many: () => {
      many = true;
    },
  };
}
