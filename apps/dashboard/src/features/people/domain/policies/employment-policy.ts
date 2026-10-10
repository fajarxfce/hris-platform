import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { AssignedEmploymentTerms } from "../entities/employment-assignments";
import type { EmploymentChange } from "../entities/employment-change";

export const canManageEmployment = (permissions: readonly string[]) =>
  permissions.includes("people.read") && permissions.includes("people.manage");

export function validateEmploymentTerms(
  terms: AssignedEmploymentTerms,
): Result<AssignedEmploymentTerms> {
  if (
    !isCalendarDate(terms.startDate) ||
    !isCalendarDate(terms.effectiveFrom) ||
    terms.effectiveFrom < terms.startDate ||
    (terms.endDate !== null && (!isCalendarDate(terms.endDate) || terms.endDate < terms.startDate))
  )
    return failed("invalid_employment_dates");
  if (
    !["PERMANENT", "FIXED_TERM"].includes(terms.contract) ||
    !["ACTIVE", "PROBATION", "SUSPENDED", "ENDED"].includes(terms.status)
  )
    return failed("invalid_employment_change");
  if (terms.contract === "FIXED_TERM" && (terms.endDate === null || terms.status === "PROBATION"))
    return failed("invalid_fixed_term_contract");
  if (terms.status === "ENDED" && terms.endDate === null) return failed("end_date_required");
  if (
    [
      terms.branchId,
      terms.departmentId,
      terms.positionId,
      terms.costCenterId,
      terms.managerId,
    ].some((id) => id !== null && !isUuid(id))
  )
    return failed("invalid_employment_change");
  return success(Object.freeze({ ...terms }));
}

export function normalizeEmploymentChange(input: EmploymentChange): Result<EmploymentChange> {
  if (
    !isUuid(input.employeeId) ||
    !Number.isSafeInteger(input.expectedVersion) ||
    input.expectedVersion < 0 ||
    input.expectedVersion >= Number.MAX_SAFE_INTEGER
  )
    return failed("invalid_employment_change");
  const reason = input.reason.trim();
  if (!reason || reason.length > 1000) return failed("reason_required");
  if (input.terms.managerId?.toLowerCase() === input.employeeId.toLowerCase())
    return failed("manager_unavailable");
  const terms = validateEmploymentTerms(input.terms);
  return terms.ok ? success(Object.freeze({ ...input, terms: terms.value, reason })) : terms;
}

export function employmentChangeWasRejected(failure: Failure): boolean {
  return [
    "invalid_employment_change",
    "reason_required",
    "invalid_employment_dates",
    "invalid_fixed_term_contract",
    "end_date_required",
    "invalid_version",
    "organization_assignment_unavailable",
    "manager_unavailable",
    "reporting_cycle_or_depth",
    "employee_not_found",
    "employment_start_immutable",
    "employment_offboarded",
    "transferred_employment_closed",
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
}
