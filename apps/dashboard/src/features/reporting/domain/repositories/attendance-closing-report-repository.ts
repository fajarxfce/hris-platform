import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type {
  AttendanceClosingEmployeePage,
  AttendanceClosingEmployeeQuery,
  AttendanceClosingReport,
} from "../entities/attendance-closing-report";

export interface AttendanceClosingReportRepository {
  employees(
    company: CompanyId,
    query: AttendanceClosingEmployeeQuery,
    signal: AbortSignal,
  ): Promise<Result<AttendanceClosingEmployeePage>>;
  load(
    company: CompanyId,
    from: string,
    until: string,
    signal: AbortSignal,
  ): Promise<Result<AttendanceClosingReport>>;
}
