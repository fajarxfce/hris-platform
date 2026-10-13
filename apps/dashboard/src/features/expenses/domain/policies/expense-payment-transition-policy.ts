import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePaymentSnapshot } from "../entities/expense-payment-snapshot";
import {
  type ExpensePaymentTransition,
  expensePaymentTransitions,
} from "../entities/expense-payment-transition";
import type { ExpensePaymentTransitionCommand } from "../entities/expense-payment-transition-command";
import { canReadExpensePayments } from "./expense-payment-read-policy";
import { expenseEvidenceUnavailable } from "./expense-read-failure-policy";
export const canTransitionExpensePayment = (
  access: CompanyAccess,
  review: ExpensePaymentSnapshot,
  transition: ExpensePaymentTransition,
): boolean =>
  access.companyId === review.companyId &&
  canReadExpensePayments(access.permissions) &&
  review.status === "PREPARED" &&
  review.availableActions.includes(transition);
export function prepareExpensePaymentTransition(
  access: CompanyAccess,
  operation: OperationId,
  review: ExpensePaymentSnapshot,
  transition: ExpensePaymentTransition,
  inputReason: string,
): Result<ExpensePaymentTransitionCommand> {
  if (access.companyId !== review.companyId || !canReadExpensePayments(access.permissions))
    return failed("access_denied");
  if (!canTransitionExpensePayment(access, review, transition))
    return failed("expense_payment_action_unavailable");
  const reason = inputReason.trim();
  if (
    !expensePaymentTransitions.includes(transition) ||
    !isUuid(operation) ||
    !isUuid(review.id) ||
    !Number.isSafeInteger(review.version) ||
    review.version < 0 ||
    review.version >= Number.MAX_SAFE_INTEGER ||
    reason.length < 1 ||
    reason.length > 1000
  )
    return failed("invalid_expense_payment_action");
  return success(Object.freeze({ id: review.id, version: review.version, transition, reason }));
}
export const expensePaymentReviewChanged = (failure: Failure): boolean =>
  [
    "stale_version",
    "expense_payment_not_prepared",
    "expense_payment_evidence_unavailable",
    "expense_payment_batch_not_found",
    "expense_payment_action_unavailable",
    "independent_payment_release_required",
    "independent_expense_payment_required",
  ].includes(failure.code);
export const expensePaymentActionWasRejected = (failure: Failure): boolean =>
  expenseEvidenceUnavailable(failure) ||
  expensePaymentReviewChanged(failure) ||
  [
    "invalid_expense_payment_action",
    "data_conflict",
    "company_required",
    "csrf_invalid",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
