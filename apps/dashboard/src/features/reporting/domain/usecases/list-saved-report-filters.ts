import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { SavedReportFilterRepository } from "../repositories/saved-report-filter-repository";
export class ListSavedReportFilters {
  constructor(private readonly filters: SavedReportFilterRepository) {}
  execute(access: CompanyAccess, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!access.permissions.includes("reports.read"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(access.companyId)) return Promise.resolve(failed("company_required"));
    return this.filters.list(access.companyId, signal);
  }
}
