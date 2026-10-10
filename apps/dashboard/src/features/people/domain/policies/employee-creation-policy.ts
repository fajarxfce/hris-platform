import { isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { EmployeeCreation } from "../entities/employee-creation";
import { isEmployeeNumber } from "./employee-policy";
import { validateEmploymentTerms } from "./employment-policy";
import { normalizePersonFields } from "./person-profile-policy";

export const canCreateEmployee = (permissions: readonly string[]): boolean =>
  permissions.includes("people.manage");
export function normalizeEmployeeCreation(input: EmployeeCreation): Result<EmployeeCreation> {
  if (!isUuid(input.employeeId) || !isUuid(input.personId))
    return failed("invalid_employee_creation");
  const employeeNumber = input.employeeNumber.trim().toUpperCase();
  if (!isEmployeeNumber(employeeNumber)) return failed("invalid_employee_number");
  const person = normalizePersonFields(input);
  if (!person.ok) return person;
  const reason = input.reason.trim();
  if (!reason || reason.length > 1000) return failed("reason_required");
  const terms = validateEmploymentTerms({ ...input, effectiveFrom: input.startDate });
  if (!terms.ok) return terms;
  if (input.managerId?.toLowerCase() === input.employeeId.toLowerCase())
    return failed("manager_unavailable");
  return success(Object.freeze({ ...input, ...person.value, employeeNumber, reason }));
}

export function employeeCreationWasRejected(failure: Failure): boolean {
  return [
    "invalid_employee_creation",
    "invalid_employment_change",
    "invalid_employee_number",
    "invalid_person",
    "reason_required",
    "invalid_employment_dates",
    "initial_effective_date_must_match_start",
    "invalid_fixed_term_contract",
    "end_date_required",
    "organization_assignment_unavailable",
    "manager_unavailable",
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
