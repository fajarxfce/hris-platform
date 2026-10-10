import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveBalanceAdjustment } from "../entities/leave-balance-adjustment";
import type { LeaveLedger } from "../entities/leave-ledger";

export const canManageLeaveBalances = (permissions: readonly string[]): boolean =>
  permissions.includes("leave.manage");

export const canAdjustLeaveBalance = (access: CompanyAccess, review: LeaveLedger): boolean =>
  access.companyId === review.companyId &&
  canManageLeaveBalances(access.permissions) &&
  !review.balance.closed &&
  review.availableActions.includes("ADJUST");

export function normalizeLeaveBalanceAdjustment(
  input: LeaveBalanceAdjustment,
): LeaveBalanceAdjustment {
  const days = input.days.trim();
  const decimal = /^[+-]?(?:0|[1-9]\d{0,2})(?:\.\d{1,2})?$/u.test(days);
  return Object.freeze({
    ...input,
    employeeId: input.employeeId.toLowerCase(),
    typeId: input.typeId.toLowerCase(),
    days: decimal && Number.isInteger(Number(days) * 2) ? String(Number(days)) : days,
    reason: input.reason.trim(),
  });
}

export function validateLeaveBalanceAdjustment(input: LeaveBalanceAdjustment): Failure | null {
  const fields: Record<string, string> = {};
  if (!isUuid(input.employeeId)) fields.employeeId = "invalid_value";
  if (!isUuid(input.typeId)) fields.typeId = "invalid_value";
  if (!Number.isInteger(input.year) || input.year < 1900 || input.year > 2200)
    fields.year = "invalid_value";
  if (
    !Number.isSafeInteger(input.expectedVersion) ||
    input.expectedVersion < 0 ||
    input.expectedVersion >= Number.MAX_SAFE_INTEGER
  )
    fields.expectedVersion = "invalid_value";
  if (
    !/^-?(?:0|[1-9]\d{0,2})(?:\.5)?$/u.test(input.days) ||
    Number(input.days) === 0 ||
    Math.abs(Number(input.days)) > 366
  )
    fields.days = "invalid_leave_days";
  if (input.reason.length === 0 || input.reason.length > 1000) fields.reason = "invalid_reason";
  return Object.keys(fields).length
    ? { code: "invalid_leave_adjustment", fields, parameters: {} }
    : null;
}

/** Only a definite first rejection permits a new command. A lost response remains unresolved. */
export const leaveBalanceAdjustmentWasRejected = (failure: Failure): boolean =>
  [
    "invalid_leave_adjustment",
    "invalid_leave_days",
    "invalid_version",
    "insufficient_leave_balance",
    "leave_balance_limit",
    "stale_balance_version",
    "leave_year_closed",
    "leave_type_unavailable",
    "leave_type_not_found",
    "employee_not_found",
    "self_adjustment_denied",
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
