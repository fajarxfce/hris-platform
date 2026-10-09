import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadHeadcount, isHeadcountDate } from "../policies/headcount-policy";
import type { HeadcountReportRepository } from "../repositories/headcount-report-repository";

export class LoadHeadcountReport {
  constructor(private readonly reports: HeadcountReportRepository) {}

  execute(access: CompanyAccess, asOf: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadHeadcount(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isHeadcountDate(asOf)) return Promise.resolve(failed("invalid_report_date"));
    return this.reports.load(access.companyId, asOf, signal);
  }
}
