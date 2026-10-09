import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { HeadcountReportRepository } from "../../domain/repositories/headcount-report-repository";
import type { HeadcountReportDataSource } from "../datasources/headcount-report-data-source";
import { toHeadcountReport } from "../mappers/headcount-report-mapper";

export class RemoteHeadcountReportRepository implements HeadcountReportRepository {
  constructor(private readonly source: HeadcountReportDataSource) {}

  load(companies: readonly CompanyId[], asOf: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toHeadcountReport(await this.source.load(companies, asOf, signal), companies, asOf),
    );
  }
}
