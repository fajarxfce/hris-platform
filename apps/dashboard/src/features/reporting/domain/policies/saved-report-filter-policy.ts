import { type CompanyId, isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { ReportFilterDefinition } from "../entities/saved-report-filter";
import { normalizeReportSelection } from "./report-selection-policy";
export const maximumSavedReportFilters = 50;
export const isReportFilterVersion = (value: number): boolean =>
  Number.isSafeInteger(value) && value >= 0 && value <= 10000;
export function normalizeReportFilter(
  company: CompanyId,
  input: ReportFilterDefinition,
): Result<ReportFilterDefinition> {
  const name = input.name.trim();
  if (
    input.name.length > 80 ||
    !name.length ||
    [...name].some((character) => {
      const code = character.charCodeAt(0);
      return code <= 31 || (code >= 127 && code <= 159);
    })
  )
    return failed("invalid_report_filter_name");
  const normalized = normalizeReportSelection(company, input);
  return normalized.ok ? success(Object.freeze({ ...normalized.value, name })) : normalized;
}

export const validReportFilterIdentity = (id: string, operation: string): boolean =>
  isUuid(id) && isUuid(operation);

/** A rejection on retry cannot settle an earlier command whose response was lost. */
export const reportFilterCommandWasRejected = (failure: Failure): boolean =>
  [
    "invalid_report_filter_name",
    "unsupported_report_definition",
    "invalid_report_filter_parameters",
    "invalid_report_date",
    "invalid_report_companies",
    "invalid_report_range",
    "invalid_version",
    "invalid_report_filter",
    "report_filter_not_found",
    "report_filter_limit",
    "report_filter_version_limit",
    "stale_version",
    "data_conflict",
    "access_denied",
    "company_access_denied",
    "company_required",
    "authentication_required",
    "session_revoked",
    "unauthenticated",
    "mfa_required",
    "mfa_setup_required",
    "recent_authentication_required",
    "csrf_invalid",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
