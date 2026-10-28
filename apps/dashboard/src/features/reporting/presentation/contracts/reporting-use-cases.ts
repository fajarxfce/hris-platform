import type { LoadAttendanceExceptionReport } from "../../domain/usecases/load-attendance-exception-report";
import type { LoadHeadcountReport } from "../../domain/usecases/load-headcount-report";

export type ReportingUseCases = Readonly<{
  loadHeadcount: Pick<LoadHeadcountReport, "execute">;
  loadAttendanceExceptions: Pick<LoadAttendanceExceptionReport, "execute">;
}>;
