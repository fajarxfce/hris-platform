import { type CompanyId, isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import { overtimeStatuses } from "../../../workforce/domain/entities/overtime-request";
import { reportDefinitions } from "../entities/report-catalogue";
import type { ReportFilterDefinition } from "../entities/saved-report-filter";
import { isAttendanceClosingReportRange } from "./attendance-closing-report-policy";
import { isAttendanceReportRange } from "./attendance-report-policy";
import { isExpenseReportRange } from "./expense-report-policy";
import { isHeadcountCompanies, isHeadcountDate } from "./headcount-policy";
import { isLeaveReportRange } from "./leave-report-policy";
import { isOvertimeReportRange } from "./overtime-report-policy";
import { isPayrollCostReportRange } from "./payroll-cost-report-policy";

export const maximumSavedReportFilters = 50;
export const isReportFilterVersion = (value: number): boolean =>
  Number.isSafeInteger(value) && value >= 0 && value <= 10000;

/** Pure canonical selection; retaining a company ID never grants access to it. */
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
  if (
    !Object.hasOwn(reportDefinitions, input.kind) ||
    reportDefinitions[input.kind] !== input.definitionVersion
  )
    return failed("unsupported_report_definition");
  const p = input.parameters;
  const invalid = () => failed("invalid_report_filter_parameters");
  if (
    p.companies.length > 32 ||
    [p.asOf, p.from, p.until].some((value) => value !== null && value.length > 10) ||
    (p.status !== null && p.status.length > 32)
  )
    return invalid();
  if (input.kind === "HEADCOUNT") {
    if (p.asOf === null || p.from !== null || p.until !== null || p.status !== null)
      return invalid();
    if (!isHeadcountDate(p.asOf)) return failed("invalid_report_date");
    const companies = p.companies.length ? p.companies : [company];
    if (!isHeadcountCompanies(companies)) return failed("invalid_report_companies");
    return success(
      Object.freeze({
        ...input,
        name,
        parameters: Object.freeze({
          ...p,
          companies: Object.freeze(companies.map((id) => id.toLowerCase() as CompanyId).sort()),
        }),
      }),
    );
  }
  if (
    p.asOf !== null ||
    p.companies.length ||
    p.from === null ||
    p.until === null ||
    (p.status !== null &&
      (input.kind !== "OVERTIME" || !overtimeStatuses.some((status) => status === p.status)))
  )
    return invalid();
  const valid = {
    ATTENDANCE_EXCEPTIONS: isAttendanceReportRange,
    ATTENDANCE_CLOSINGS: isAttendanceClosingReportRange,
    LEAVE: isLeaveReportRange,
    OVERTIME: isOvertimeReportRange,
    EXPENSES: isExpenseReportRange,
    PAYROLL_COSTS: isPayrollCostReportRange,
  }[input.kind](p.from, p.until);
  if (!valid) return invalid();
  return success(
    Object.freeze({
      ...input,
      name,
      parameters: Object.freeze({ ...p, companies: Object.freeze([]) }),
    }),
  );
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
