import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { OvertimeStatus } from "../../../workforce/domain/entities/overtime-request";
import type { OvertimeReportRepository } from "../../domain/repositories/overtime-report-repository";
import type { OvertimeReportDataSource } from "../datasources/overtime-report-data-source";
import { toOvertimeReport } from "../mappers/overtime-report-mapper";

export class RemoteOvertimeReportRepository implements OvertimeReportRepository {
  constructor(private readonly source: OvertimeReportDataSource) {}
  load(
    company: CompanyId,
    from: string,
    until: string,
    status: OvertimeStatus | null,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toOvertimeReport(
        await this.source.load(company, from, until, status, signal),
        company,
        from,
        until,
        status,
      ),
    );
  }
}
