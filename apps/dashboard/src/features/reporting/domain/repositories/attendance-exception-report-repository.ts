import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { AttendanceExceptionReport } from "../entities/attendance-exception-report";

export interface AttendanceExceptionReportRepository {
  load(
    company: CompanyId,
    from: string,
    until: string,
    signal: AbortSignal,
  ): Promise<Result<AttendanceExceptionReport>>;
}
