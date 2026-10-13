import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePaymentOutcome } from "../entities/expense-payment-outcome";
import type { ExpenseReconciliationCommand } from "../entities/expense-reconciliation-command";
import type { ExpenseReconciliationSnapshot } from "../entities/expense-reconciliation-snapshot";
import { canReadExpensePayments } from "./expense-payment-read-policy";
import {
  expensePaymentActionWasRejected,
  expensePaymentReviewChanged,
} from "./expense-payment-transition-policy";
export const canReconcileExpensePayment = (
  access: CompanyAccess,
  review: ExpenseReconciliationSnapshot,
): boolean =>
  access.companyId === review.companyId &&
  canReadExpensePayments(access.permissions) &&
  review.status === "RELEASED" &&
  review.releasedAt !== null &&
  review.pendingItemIds.length > 0 &&
  review.availableActions.includes("RECONCILE");
/** Input policy; the API checks the upper time bound against its own clock at commit. */
export function prepareExpensePaymentOutcome(
  review: ExpenseReconciliationSnapshot,
  input: ExpensePaymentOutcome,
): Result<ExpensePaymentOutcome> {
  if (!isUuid(input.itemId) || !review.pendingItemIds.includes(input.itemId.toLowerCase()))
    return failed("expense_payment_item_not_pending");
  const released = review.releasedAt ? utcInstantMicroseconds(review.releasedAt) : null,
    occurred = utcInstantMicroseconds(input.occurredAt);
  const reason = input.reason.trim(),
    reference = input.transactionReference?.trim() ?? null;
  if (
    released === null ||
    occurred === null ||
    occurred < released ||
    reason.length < 1 ||
    reason.length > 1000 ||
    typeof input.confirmedNoTransfer !== "boolean" ||
    (input.status === "SUCCEEDED"
      ? reference === null ||
        !/^[A-Za-z0-9][A-Za-z0-9._:/ -]{0,99}$/u.test(reference) ||
        input.confirmedNoTransfer
      : input.status !== "FAILED" || reference !== null || !input.confirmedNoTransfer)
  )
    return failed("invalid_expense_payment_result");
  return success(
    Object.freeze({
      itemId: input.itemId.toLowerCase(),
      status: input.status,
      transactionReference: reference,
      occurredAt: input.occurredAt,
      reason,
      confirmedNoTransfer: input.confirmedNoTransfer,
    }),
  );
}
export function prepareExpenseReconciliation(
  access: CompanyAccess,
  operation: OperationId,
  review: ExpenseReconciliationSnapshot,
  outcomes: readonly ExpensePaymentOutcome[],
  inputReason: string,
): Result<ExpenseReconciliationCommand> {
  if (access.companyId !== review.companyId || !canReadExpensePayments(access.permissions))
    return failed("access_denied");
  if (!canReconcileExpensePayment(access, review))
    return failed("expense_reconciliation_unavailable");
  const reason = inputReason.trim();
  if (
    !isUuid(operation) ||
    !isUuid(review.id) ||
    !Number.isSafeInteger(review.version) ||
    review.version < 1 ||
    review.version >= Number.MAX_SAFE_INTEGER ||
    reason.length < 1 ||
    reason.length > 1000
  )
    return failed("invalid_expense_payment_action");
  if (
    outcomes.length < 1 ||
    outcomes.length > 100 ||
    new Set(outcomes.map((item) => item.itemId.toLowerCase())).size !== outcomes.length
  )
    return failed("invalid_expense_payment_results");
  const results: ExpensePaymentOutcome[] = [];
  for (const input of outcomes) {
    const prepared = prepareExpensePaymentOutcome(review, input);
    if (!prepared.ok) return prepared;
    results.push(prepared.value);
  }
  const references = results.flatMap((result) =>
    result.transactionReference === null ? [] : [result.transactionReference],
  );
  if (new Set(references).size !== references.length)
    return failed("duplicate_payment_transaction_reference");
  return success(
    Object.freeze({
      id: review.id,
      version: review.version,
      results: Object.freeze(results),
      reason,
    }),
  );
}
export const expenseReconciliationReviewChanged = (failure: Failure): boolean =>
  expensePaymentReviewChanged(failure) ||
  [
    "expense_payment_not_released",
    "expense_payment_item_not_pending",
    "expense_reconciliation_unavailable",
  ].includes(failure.code);
export const expenseReconciliationWasRejected = (failure: Failure): boolean =>
  expensePaymentActionWasRejected(failure) ||
  expenseReconciliationReviewChanged(failure) ||
  [
    "invalid_expense_payment_results",
    "invalid_expense_payment_result",
    "duplicate_payment_transaction_reference",
  ].includes(failure.code);
