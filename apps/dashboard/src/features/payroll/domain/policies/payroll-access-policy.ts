import type { Failure } from "../../../../core/domain/result";

export const canReadPayrollPolicy = (permissions: readonly string[]): boolean =>
  permissions.some((permission) =>
    ["payroll.read", "payroll.policy.manage", "payroll.compensation.manage"].includes(permission),
  );
export const canManagePayrollPolicy = (permissions: readonly string[]): boolean =>
  permissions.includes("payroll.policy.manage");

export const payrollPolicyAccessLost = (failure: Failure): boolean =>
  [
    "access_denied",
    "company_access_denied",
    "company_not_found",
    "authentication_required",
    "unauthenticated",
    "session_revoked",
    "company_module_disabled",
    "company_maintenance",
    "client_update_required",
  ].includes(failure.code);

/** A rejected retry does not resolve an earlier command with an unknown outcome. */
export const payrollPolicySaveWasRejected = (failure: Failure): boolean =>
  payrollPolicyAccessLost(failure) ||
  [
    "mfa_required",
    "mfa_setup_required",
    "invalid_payroll_policy",
    "invalid_decimal_amount",
    "invalid_version",
    "payroll_policy_revision_limit",
    "payroll_period_frozen",
    "stale_version",
    "data_conflict",
    "company_required",
    "recent_authentication_required",
    "csrf_invalid",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
