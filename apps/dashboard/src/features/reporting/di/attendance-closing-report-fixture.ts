import type { CompanyId } from "../../../core/domain/identifiers";
import type {
  AttendanceClosingEmployeePage,
  AttendanceClosingReport,
} from "../domain/entities/attendance-closing-report";
export const closingCompany = "77ed1727-6b89-48f9-ae10-f5f3f87c93f0" as CompanyId;
export const closingAccess = {
  companyId: closingCompany,
  permissions: ["reports.read", "workforce.read"],
};
export const closingReference = {
  periodId: "55555555-5555-4555-8555-555555555555",
  jobId: "66666666-6666-4666-8666-666666666666",
  version: 2,
  closedAt: "2026-10-01T00:00:00Z",
  timezone: "Asia/Jakarta",
};
export const closingCounts = {
  closedMonths: 1,
  employmentMonths: 1,
  employments: 1,
  adjustedEmploymentMonths: 1,
  days: 30,
  scheduledDays: 2,
  scheduledMinutes: 960,
  original: {
    workedDays: 1,
    absenceDays: 0,
    unrecordedDays: 1,
    offDays: 0,
    unassignedDays: 28,
    acceptedMinutes: 480,
  },
  current: {
    workedDays: 2,
    absenceDays: 0,
    unrecordedDays: 0,
    offDays: 0,
    unassignedDays: 28,
    acceptedMinutes: 900,
  },
};
export const closingReport: AttendanceClosingReport = {
  companyId: closingCompany,
  from: "2026-09",
  until: "2026-09",
  evaluatedAt: "2026-10-03T00:00:00Z",
  definitionVersion: "attendance-closings.v1",
  totals: closingCounts,
  months: [{ month: "2026-09", closing: closingReference, counts: closingCounts }],
};
export const closingEmployeePage: AttendanceClosingEmployeePage = {
  companyId: closingCompany,
  month: "2026-09",
  evaluatedAt: closingReport.evaluatedAt,
  definitionVersion: "attendance-closings.v1",
  items: [
    {
      employeeId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      employeeNumber: "C001",
      employeeName: "Closing employee",
      closing: closingReference,
      adjustment: {
        id: "77777777-7777-4777-8777-777777777777",
        revision: 2,
        recordedAt: "2026-10-02T00:00:00Z",
      },
      counts: closingCounts,
    },
  ],
  nextCursor: null,
};
