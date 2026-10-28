import type { LoadAttendanceExceptionReport } from "../../domain/usecases/load-attendance-exception-report";
import type { LoadHeadcountReport } from "../../domain/usecases/load-headcount-report";
import type { LoadLeaveReport } from "../../domain/usecases/load-leave-report";
import type { LoadLeaveReportRequests } from "../../domain/usecases/load-leave-report-requests";

export type ReportingUseCases = Readonly<{
  loadLeave: Pick<LoadLeaveReport, "execute">;
  loadLeaveRequests: Pick<LoadLeaveReportRequests, "execute">;
  loadHeadcount: Pick<LoadHeadcountReport, "execute">;
  loadAttendanceExceptions: Pick<LoadAttendanceExceptionReport, "execute">;
}>;
