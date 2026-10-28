import type { ReportKind } from "../entities/report-catalogue";
import { canReadAttendanceReport } from "./attendance-report-policy";
import { canReadExpenseReport } from "./expense-report-policy";
import { canReadHeadcount } from "./headcount-policy";
import { canReadLeaveReport } from "./leave-report-policy";
import { canReadOvertimeReport } from "./overtime-report-policy";
import { canReadPayrollCostReport } from "./payroll-cost-report-policy";

export function canReadReportKind(permissions: readonly string[], kind: ReportKind): boolean {
  switch (kind) {
    case "HEADCOUNT":
      return canReadHeadcount(permissions);
    case "ATTENDANCE_EXCEPTIONS":
    case "ATTENDANCE_CLOSINGS":
      return canReadAttendanceReport(permissions);
    case "LEAVE":
      return canReadLeaveReport(permissions);
    case "OVERTIME":
      return canReadOvertimeReport(permissions);
    case "EXPENSES":
      return canReadExpenseReport(permissions);
    case "PAYROLL_COSTS":
      return canReadPayrollCostReport(permissions);
  }
}
