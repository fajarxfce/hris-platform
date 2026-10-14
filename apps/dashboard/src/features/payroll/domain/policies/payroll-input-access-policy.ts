import type { Failure } from "../../../../core/domain/result";
export const canReadPayrollInputs = (permissions: readonly string[]): boolean =>
  permissions.some((value) =>
    ["payroll.read", "payroll.calculate", "payroll.review"].includes(value),
  );
export const payrollInputAccessLost = (failure: Failure): boolean =>
  [
    "access_denied",
    "company_access_denied",
    "company_not_found",
    "employee_not_found",
    "authentication_required",
    "unauthenticated",
    "session_revoked",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
  ].includes(failure.code);
export const payrollInputNeedsReview = (failure: Failure): boolean =>
  [
    "stale_version",
    "stale_employment_version",
    "stale_workforce_version",
    "payroll_period_frozen",
    "payroll_workforce_not_closed",
    "payroll_workforce_employee_missing",
    "payroll_input_not_found",
    "payroll_input_already_verified",
    "payroll_input_revision_limit",
  ].includes(failure.code);
export const payrollInputCommandWasRejected = (failure: Failure): boolean =>
  payrollInputAccessLost(failure) ||
  payrollInputNeedsReview(failure) ||
  [
    "mfa_required",
    "mfa_setup_required",
    "self_payroll_input_change_denied",
    "self_payroll_input_review_denied",
    "payroll_input_author_cannot_verify",
    "invalid_payroll_input",
    "invalid_decimal_amount",
    "invalid_version",
    "payroll_amount_limit",
    "data_conflict",
    "company_required",
    "recent_authentication_required",
    "csrf_invalid",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
    "payroll_input_action_unavailable",
  ].includes(failure.code);
