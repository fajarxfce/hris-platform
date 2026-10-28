import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DeleteReportFilterChange } from "../entities/saved-report-filter";
import {
  isReportFilterVersion,
  validReportFilterIdentity,
} from "../policies/saved-report-filter-policy";
import type { SavedReportFilterRepository } from "../repositories/saved-report-filter-repository";
export class DeleteReportFilter {
  constructor(private readonly filters: SavedReportFilterRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: DeleteReportFilterChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!access.permissions.includes("reports.read"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(access.companyId)) return Promise.resolve(failed("company_required"));
    if (!validReportFilterIdentity(input.id, operation))
      return Promise.resolve(failed("invalid_report_filter"));
    if (!isReportFilterVersion(input.expectedVersion))
      return Promise.resolve(failed("invalid_version"));
    return this.filters.delete(
      access.companyId,
      operation,
      Object.freeze({ id: input.id.toLowerCase(), expectedVersion: input.expectedVersion }),
      signal,
    );
  }
}
