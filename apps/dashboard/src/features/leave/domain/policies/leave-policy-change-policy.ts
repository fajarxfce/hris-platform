import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { LeavePolicyChange } from "../entities/leave-policy-change";

export function normalizeLeavePolicyChange(input: LeavePolicyChange): LeavePolicyChange {
  return Object.freeze({
    ...input,
    id: input.id.toLowerCase(),
    code: input.code.trim().toUpperCase(),
    name: input.name.trim(),
    effectiveFrom: input.effectiveFrom.trim(),
    reason: input.reason.trim(),
    allowedContracts: Object.freeze([...input.allowedContracts].sort()),
    accrual: input.accrual
      ? Object.freeze({
          ...input.accrual,
          daysPerPeriod: input.accrual.daysPerPeriod.trim().replace(/\.0$/u, ""),
          carryLimitDays: input.accrual.carryLimitDays.trim().replace(/\.0$/u, ""),
        })
      : null,
  });
}

export function validateLeavePolicyChange(input: LeavePolicyChange): Failure | null {
  const fields: Record<string, string> = {};
  if (!isUuid(input.id)) fields.id = "invalid_value";
  if (!/^[A-Z][A-Z0-9_-]{0,31}$/u.test(input.code)) fields.code = "invalid_code";
  if (!input.name.length || input.name.length > 200) fields.name = "invalid_name";
  if (!input.reason.length || input.reason.length > 1000) fields.reason = "invalid_reason";
  if (
    !isCalendarDate(input.effectiveFrom) ||
    input.effectiveFrom < "1900-01-01" ||
    input.effectiveFrom > "2200-12-31"
  )
    fields.effectiveFrom = "invalid_date";
  if (
    !Number.isInteger(input.minServiceMonths) ||
    input.minServiceMonths < 0 ||
    input.minServiceMonths > 120
  )
    fields.minServiceMonths = "out_of_range";
  if (
    !Number.isInteger(input.maxRequestDays) ||
    input.maxRequestDays < 1 ||
    input.maxRequestDays > 366
  )
    fields.maxRequestDays = "out_of_range";
  if (
    input.allowedContracts.length < 1 ||
    input.allowedContracts.length > 2 ||
    new Set(input.allowedContracts).size !== input.allowedContracts.length ||
    input.allowedContracts.some((kind) => !["PERMANENT", "FIXED_TERM"].includes(kind))
  )
    fields.allowedContracts = "invalid_contracts";
  if (
    input.expectedVersion !== null &&
    (!Number.isSafeInteger(input.expectedVersion) ||
      input.expectedVersion < 0 ||
      input.expectedVersion >= Number.MAX_SAFE_INTEGER)
  )
    fields.expectedVersion = "invalid_value";
  if (Object.keys(fields).length) return { code: "invalid_leave_policy", fields, parameters: {} };
  const accrual = input.accrual;
  if (!accrual) return null;
  if (!["MANUAL", "MONTHLY", "ANNUAL"].includes(accrual.frequency))
    fields["accrual.frequency"] = "invalid_value";
  const days = Number(accrual.daysPerPeriod);
  const carry = Number(accrual.carryLimitDays);
  const maximum = accrual.frequency === "MONTHLY" ? 31 : accrual.frequency === "MANUAL" ? 0 : 366;
  const minimum = accrual.frequency === "MANUAL" ? 0 : 0.5;
  if (
    !/^(?:0|[1-9]\d{0,2})(?:\.5)?$/u.test(accrual.daysPerPeriod) ||
    days < minimum ||
    days > maximum ||
    (!input.allowPartialDays && !Number.isInteger(days))
  )
    fields["accrual.daysPerPeriod"] = "out_of_range";
  if (
    !/^(?:0|[1-9]\d{0,2})(?:\.5)?$/u.test(accrual.carryLimitDays) ||
    carry > 366 ||
    (!input.allowPartialDays && !Number.isInteger(carry))
  )
    fields["accrual.carryLimitDays"] = "out_of_range";
  return Object.keys(fields).length
    ? { code: "invalid_leave_accrual_policy", fields, parameters: {} }
    : null;
}

/** A later rejection cannot resolve an earlier command whose response was lost. */
export const leavePolicySaveWasRejected = (failure: Failure): boolean =>
  [
    "invalid_leave_policy",
    "invalid_leave_accrual_policy",
    "invalid_leave_days",
    "invalid_version",
    "leave_type_code_immutable",
    "leave_type_not_found",
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
