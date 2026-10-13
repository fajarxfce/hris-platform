import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { ExpensePolicyChange } from "../entities/expense-policy-change";

import { expenseAmountMinor } from "./expense-amount";

export function normalizeExpensePolicyChange(input: ExpensePolicyChange): ExpensePolicyChange {
  return Object.freeze({
    ...input,
    id: input.id.toLowerCase(),
    code: input.code.trim().toUpperCase(),
    name: input.name.trim(),
    effectiveFrom: input.effectiveFrom.trim(),
    reason: input.reason.trim(),
    maximumLineAmount: input.maximumLineAmount.trim(),
    maximumClaimAmount: input.maximumClaimAmount.trim(),
    allowedContracts: Object.freeze([...input.allowedContracts].sort()),
  });
}

export function validateExpensePolicyChange(input: ExpensePolicyChange): Failure | null {
  const fields: Record<string, string> = {};
  if (!isUuid(input.id)) fields.id = "invalid_value";
  if (!/^[A-Z][A-Z0-9_]{1,31}$/u.test(input.code)) fields.code = "invalid_code";
  if (!input.name.length || input.name.length > 120) fields.name = "invalid_name";
  if (!input.reason.length || input.reason.length > 1000) fields.reason = "invalid_reason";
  if (
    !isCalendarDate(input.effectiveFrom) ||
    input.effectiveFrom < "1900-01-01" ||
    input.effectiveFrom > "2200-12-31"
  )
    fields.effectiveFrom = "invalid_date";
  const line = expenseAmountMinor(input.maximumLineAmount);
  const claim = expenseAmountMinor(input.maximumClaimAmount);
  if (line === null || line <= 0n) fields.maximumLineAmount = "invalid_amount";
  if (claim === null || claim <= 0n) fields.maximumClaimAmount = "invalid_amount";
  if (line !== null && claim !== null && claim < line)
    fields.maximumClaimAmount = "claim_below_line";
  if (
    !Number.isInteger(input.maximumAgeDays) ||
    input.maximumAgeDays < 1 ||
    input.maximumAgeDays > 366
  )
    fields.maximumAgeDays = "out_of_range";
  if (
    input.allowedContracts.length < 1 ||
    input.allowedContracts.length > 2 ||
    new Set(input.allowedContracts).size !== input.allowedContracts.length ||
    input.allowedContracts.some((kind) => !["PERMANENT", "FIXED_TERM"].includes(kind))
  )
    fields.allowedContracts = "invalid_contracts";
  if (
    input.expectedVersion !== null &&
    (!Number.isInteger(input.expectedVersion) ||
      input.expectedVersion < 0 ||
      input.expectedVersion > 999)
  )
    fields.expectedVersion = "invalid_value";
  return Object.keys(fields).length
    ? { code: "invalid_expense_policy", fields, parameters: {} }
    : null;
}

/** A later rejection cannot resolve a previous command whose response was lost. */
export const expensePolicySaveWasRejected = (failure: Failure): boolean =>
  [
    "invalid_expense_policy",
    "invalid_expense_category",
    "invalid_version",
    "expense_category_code_immutable",
    "expense_category_not_found",
    "expense_category_limit",
    "expense_policy_revision_limit",
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
