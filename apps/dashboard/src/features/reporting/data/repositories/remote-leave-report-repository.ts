import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveReportQuery } from "../../domain/entities/leave-report-request";
import type { LeaveReportRepository } from "../../domain/repositories/leave-report-repository";
import type { LeaveReportDataSource } from "../datasources/leave-report-data-source";
import { toLeaveReport } from "../mappers/leave-report-mapper";
import { toLeaveReportRequestPage } from "../mappers/leave-report-request-mapper";

export class RemoteLeaveReportRepository implements LeaveReportRepository {
  constructor(private readonly source: LeaveReportDataSource) {}

  requests(company: CompanyId, query: LeaveReportQuery, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveReportRequestPage(
        await this.source.requests(
          company,
          query.from,
          query.until,
          query.status,
          query.after,
          signal,
        ),
        company,
        query,
      ),
    );
  }

  load(company: CompanyId, from: string, until: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveReport(await this.source.load(company, from, until, signal), company, from, until),
    );
  }
}
