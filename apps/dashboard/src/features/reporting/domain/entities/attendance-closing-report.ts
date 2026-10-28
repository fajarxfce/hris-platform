import type { CompanyId } from "../../../../core/domain/identifiers";
export type AttendanceClosingFacts = Readonly<{
  workedDays: number;
  absenceDays: number;
  unrecordedDays: number;
  offDays: number;
  unassignedDays: number;
  acceptedMinutes: number;
}>;
export type AttendanceClosingCounts = Readonly<{
  closedMonths: number;
  employmentMonths: number;
  employments: number;
  adjustedEmploymentMonths: number;
  days: number;
  scheduledDays: number;
  scheduledMinutes: number;
  original: AttendanceClosingFacts;
  current: AttendanceClosingFacts;
}>;
export type AttendanceClosingReference = Readonly<{
  periodId: string;
  jobId: string;
  version: number;
  closedAt: string;
  timezone: string;
}>;
export type AttendanceClosingReport = Readonly<{
  companyId: CompanyId;
  from: string;
  until: string;
  evaluatedAt: string;
  definitionVersion: "attendance-closings.v1";
  totals: AttendanceClosingCounts;
  months: readonly Readonly<{
    month: string;
    closing: AttendanceClosingReference | null;
    counts: AttendanceClosingCounts;
  }>[];
}>;
export type AttendanceClosingEmployee = Readonly<{
  employeeId: string;
  employeeNumber: string;
  employeeName: string;
  closing: AttendanceClosingReference;
  adjustment: Readonly<{ id: string; revision: number; recordedAt: string }> | null;
  counts: AttendanceClosingCounts;
}>;
export type AttendanceClosingEmployeeQuery = Readonly<{ month: string; after: string | null }>;
export type AttendanceClosingEmployeePage = Readonly<{
  companyId: CompanyId;
  month: string;
  evaluatedAt: string;
  definitionVersion: "attendance-closings.v1";
  items: readonly AttendanceClosingEmployee[];
  nextCursor: string | null;
}>;
