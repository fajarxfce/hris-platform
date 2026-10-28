import type { CompanyId } from "../../../../core/domain/identifiers";

export const reportDefinitions = Object.freeze({
  HEADCOUNT: "headcount.v1",
  ATTENDANCE_EXCEPTIONS: "attendance-exceptions.v1",
  ATTENDANCE_CLOSINGS: "attendance-closings.v1",
  LEAVE: "leave.v1",
  OVERTIME: "overtime.v1",
  EXPENSES: "expenses.v1",
  PAYROLL_COSTS: "payroll-costs.v1",
} as const);
export type ReportKind = keyof typeof reportDefinitions;
export type ReportCatalogue = Readonly<{
  companyId: CompanyId;
  evaluatedAt: string;
  reports: readonly Readonly<{ kind: ReportKind; definitionVersion: string }>[];
}>;
