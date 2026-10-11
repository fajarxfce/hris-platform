import { type CompanyId, isUuid } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { EmploymentTransferInput } from "../entities/employment-transfer-input";
import { isEmployeeNumber } from "./employee-policy";
import { employmentChangeWasRejected, validateEmploymentTerms } from "./employment-policy";

export const canTransferEmployment = (permissions: readonly string[]): boolean =>
  ["people.read", "people.manage", "people.transfer"].every((permission) =>
    permissions.includes(permission),
  );

export function normalizeEmploymentTransfer(
  companyId: CompanyId,
  input: EmploymentTransferInput,
): Result<EmploymentTransferInput> {
  if (
    !isUuid(input.employeeId) ||
    !isUuid(input.targetEmploymentId) ||
    !isUuid(input.targetCompanyId) ||
    input.targetCompanyId.toLowerCase() === companyId ||
    !Number.isSafeInteger(input.expectedVersion) ||
    input.expectedVersion < 0 ||
    input.expectedVersion >= Number.MAX_SAFE_INTEGER
  )
    return failed("invalid_employment_transfer");
  const employeeNumber = input.employeeNumber.trim().toUpperCase();
  if (!isEmployeeNumber(employeeNumber)) return failed("invalid_employee_number");
  const reason = input.reason.trim();
  if (!reason || reason.length > 1000) return failed("reason_required");
  const terms = validateEmploymentTerms(input.terms);
  if (!terms.ok) return terms;
  if (
    terms.value.effectiveFrom !== terms.value.startDate ||
    !["ACTIVE", "PROBATION"].includes(terms.value.status) ||
    terms.value.managerId?.toLowerCase() === input.targetEmploymentId.toLowerCase()
  )
    return failed("invalid_transfer_terms");
  return success(Object.freeze({ ...input, employeeNumber, reason, terms: terms.value }));
}

export function employmentTransferWasRejected(failure: Failure): boolean {
  return (
    employmentChangeWasRejected(failure) ||
    [
      "invalid_employment_transfer",
      "invalid_employee_number",
      "invalid_transfer_terms",
      "transfer_access_required",
      "transfer_requires_current_company_date",
      "source_employment_not_transferable",
      "destination_employment_exists",
      "destination_membership_required",
      "open_lifecycle_cases_pending",
      "scheduled_employment_changes_pending",
      "reporting_reassignment_required",
      "offboarding_access_required",
      "cannot_remove_own_administration",
      "last_company_administrator",
      "company_not_found",
    ].includes(failure.code)
  );
}
