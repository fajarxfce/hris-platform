import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { SavedReportFilterRepository } from "../repositories/saved-report-filter-repository";
export class GetSavedReportFilter {
  constructor(private readonly filters: SavedReportFilterRepository) {}
  execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!access.permissions.includes("reports.read"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(access.companyId)) return Promise.resolve(failed("company_required"));
    if (!isUuid(id)) return Promise.resolve(failed("invalid_report_filter"));
    return this.filters.get(access.companyId, id.toLowerCase(), signal);
  }
}
