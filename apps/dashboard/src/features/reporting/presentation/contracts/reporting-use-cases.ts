import type { LoadAttendanceClosingEmployees } from "../../domain/usecases/load-attendance-closing-employees";
import type { LoadAttendanceClosingReport } from "../../domain/usecases/load-attendance-closing-report";
import type { LoadAttendanceExceptionReport } from "../../domain/usecases/load-attendance-exception-report";
import type { LoadExpenseReport } from "../../domain/usecases/load-expense-report";
import type { LoadExpenseReportClaims } from "../../domain/usecases/load-expense-report-claims";
import type { LoadHeadcountReport } from "../../domain/usecases/load-headcount-report";
import type { LoadLeaveReport } from "../../domain/usecases/load-leave-report";
import type { LoadLeaveReportRequests } from "../../domain/usecases/load-leave-report-requests";
import type { LoadOvertimeReport } from "../../domain/usecases/load-overtime-report";
import type { LoadPayrollCostReport } from "../../domain/usecases/load-payroll-cost-report";
import type { LoadReportCatalogue } from "../../domain/usecases/load-report-catalogue";

export type ReportingUseCases = Readonly<{
  loadCatalogue: Pick<LoadReportCatalogue, "execute">;
  loadAttendanceClosings: Pick<LoadAttendanceClosingReport, "execute">;
  loadAttendanceClosingEmployees: Pick<LoadAttendanceClosingEmployees, "execute">;
  loadPayrollCosts: Pick<LoadPayrollCostReport, "execute">;
  loadExpenses: Pick<LoadExpenseReport, "execute">;
  loadExpenseClaims: Pick<LoadExpenseReportClaims, "execute">;
  loadOvertime: Pick<LoadOvertimeReport, "execute">;
  loadLeave: Pick<LoadLeaveReport, "execute">;
  loadLeaveRequests: Pick<LoadLeaveReportRequests, "execute">;
  loadHeadcount: Pick<LoadHeadcountReport, "execute">;
  loadAttendanceExceptions: Pick<LoadAttendanceExceptionReport, "execute">;
}>;
