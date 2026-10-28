import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { SaveReportFilterChange } from "../entities/saved-report-filter";
import { canReadReportKind } from "../policies/report-catalogue-policy";
import {
  isReportFilterVersion,
  normalizeReportFilter,
  validReportFilterIdentity,
} from "../policies/saved-report-filter-policy";
import type { SavedReportFilterRepository } from "../repositories/saved-report-filter-repository";
export class SaveReportFilter {
  constructor(private readonly filters: SavedReportFilterRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: SaveReportFilterChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadReportKind(access.permissions, input.kind))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(access.companyId)) return Promise.resolve(failed("company_required"));
    if (!validReportFilterIdentity(input.id, operation))
      return Promise.resolve(failed("invalid_report_filter"));
    if (input.expectedVersion !== null && !isReportFilterVersion(input.expectedVersion))
      return Promise.resolve(failed("invalid_version"));
    const normalized = normalizeReportFilter(access.companyId, input);
    if (!normalized.ok) return Promise.resolve(normalized);
    return this.filters.save(
      access.companyId,
      operation,
      Object.freeze({
        ...normalized.value,
        id: input.id.toLowerCase(),
        expectedVersion: input.expectedVersion,
      }),
      signal,
    );
  }
}
