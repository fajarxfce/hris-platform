import type { Failure } from "../../../../core/domain/result";
export const canReadCompensation = (permissions: readonly string[]): boolean =>
  permissions.some((permission) =>
    ["payroll.read", "payroll.compensation.manage"].includes(permission),
  );
export const canChangeCompensation = (permissions: readonly string[]): boolean =>
  permissions.includes("payroll.compensation.manage");
export const compensationAccessLost = (failure: Failure): boolean =>
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
export const compensationSaveWasRejected = (failure: Failure): boolean =>
  compensationAccessLost(failure) ||
  [
    "mfa_required",
    "mfa_setup_required",
    "self_compensation_change_denied",
    "invalid_compensation",
    "invalid_compensation_amount",
    "invalid_payroll_pay_basis",
    "invalid_decimal_amount",
    "invalid_version",
    "compensation_revision_limit",
    "stale_version",
    "stale_employment_version",
    "payroll_period_frozen",
    "data_conflict",
    "company_required",
    "recent_authentication_required",
    "csrf_invalid",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
