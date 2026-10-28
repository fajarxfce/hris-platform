import type { LoadAttendanceExceptionReport } from "../../domain/usecases/load-attendance-exception-report";
import type { LoadExpenseReport } from "../../domain/usecases/load-expense-report";
import type { LoadExpenseReportClaims } from "../../domain/usecases/load-expense-report-claims";
import type { LoadHeadcountReport } from "../../domain/usecases/load-headcount-report";
import type { LoadLeaveReport } from "../../domain/usecases/load-leave-report";
import type { LoadLeaveReportRequests } from "../../domain/usecases/load-leave-report-requests";
import type { LoadOvertimeReport } from "../../domain/usecases/load-overtime-report";

export type ReportingUseCases = Readonly<{
  loadExpenses: Pick<LoadExpenseReport, "execute">;
  loadExpenseClaims: Pick<LoadExpenseReportClaims, "execute">;
  loadOvertime: Pick<LoadOvertimeReport, "execute">;
  loadLeave: Pick<LoadLeaveReport, "execute">;
  loadLeaveRequests: Pick<LoadLeaveReportRequests, "execute">;
  loadHeadcount: Pick<LoadHeadcountReport, "execute">;
  loadAttendanceExceptions: Pick<LoadAttendanceExceptionReport, "execute">;
}>;
