import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { LeaveReport } from "../entities/leave-report";
import type { LeaveReportQuery, LeaveReportRequestPage } from "../entities/leave-report-request";

export interface LeaveReportRepository {
  requests(
    company: CompanyId,
    query: LeaveReportQuery,
    signal: AbortSignal,
  ): Promise<Result<LeaveReportRequestPage>>;
  load(
    company: CompanyId,
    from: string,
    until: string,
    signal: AbortSignal,
  ): Promise<Result<LeaveReport>>;
}
