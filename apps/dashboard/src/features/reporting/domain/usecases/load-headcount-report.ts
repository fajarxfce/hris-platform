import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  canReadHeadcount,
  isHeadcountCompanies,
  isHeadcountDate,
} from "../policies/headcount-policy";
import type { HeadcountReportRepository } from "../repositories/headcount-report-repository";

export class LoadHeadcountReport {
  constructor(private readonly reports: HeadcountReportRepository) {}

  execute(
    access: CompanyAccess,
    asOf: string,
    signal: AbortSignal,
    companies: readonly CompanyId[] = [access.companyId],
  ) {
    signal.throwIfAborted();
    if (!canReadHeadcount(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isHeadcountDate(asOf)) return Promise.resolve(failed("invalid_report_date"));
    if (!isHeadcountCompanies(companies))
      return Promise.resolve(failed("invalid_report_companies"));
    return this.reports.load(
      Object.freeze(companies.map((id) => id.toLowerCase() as CompanyId).sort()),
      asOf,
      signal,
    );
  }
}
