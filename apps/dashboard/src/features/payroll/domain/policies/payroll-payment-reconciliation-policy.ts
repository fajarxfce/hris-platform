import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentOutcome } from "../entities/payroll-payment-outcome";
import type { PayrollPaymentReconciliationCommand } from "../entities/payroll-payment-reconciliation-command";
import type { PayrollPaymentReconciliationSnapshot } from "../entities/payroll-payment-reconciliation-snapshot";
import { canReadPayrollPayments } from "./payroll-payment-read-policy";
import {
  payrollPaymentActionWasRejected,
  payrollPaymentReviewChanged,
} from "./payroll-payment-transition-policy";
export const canReconcilePayrollPayment = (
  access: CompanyAccess,
  review: PayrollPaymentReconciliationSnapshot,
): boolean =>
  access.companyId === review.companyId &&
  canReadPayrollPayments(access.permissions) &&
  review.status === "RELEASED" &&
  review.releasedAt !== null &&
  review.pendingItemIds.length > 0 &&
  review.availableActions.includes("RECONCILE");
/** Input policy; the API checks the upper time bound against its own clock at commit. */
export function preparePayrollPaymentOutcome(
  review: PayrollPaymentReconciliationSnapshot,
  input: PayrollPaymentOutcome,
): Result<PayrollPaymentOutcome> {
  if (!isUuid(input.itemId) || !review.pendingItemIds.includes(input.itemId.toLowerCase()))
    return failed("payroll_payment_item_not_pending");
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
    return failed("invalid_payroll_payment_result");
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
export function preparePayrollPaymentReconciliation(
  access: CompanyAccess,
  operation: OperationId,
  review: PayrollPaymentReconciliationSnapshot,
  outcomes: readonly PayrollPaymentOutcome[],
  inputReason: string,
): Result<PayrollPaymentReconciliationCommand> {
  if (access.companyId !== review.companyId || !canReadPayrollPayments(access.permissions))
    return failed("access_denied");
  if (!canReconcilePayrollPayment(access, review))
    return failed("payroll_reconciliation_unavailable");
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
    return failed("invalid_payroll_payment_action");
  if (
    outcomes.length < 1 ||
    outcomes.length > 100 ||
    new Set(outcomes.map((item) => item.itemId.toLowerCase())).size !== outcomes.length
  )
    return failed("invalid_payroll_payment_results");
  const results: PayrollPaymentOutcome[] = [];
  for (const input of outcomes) {
    const prepared = preparePayrollPaymentOutcome(review, input);
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
export const payrollPaymentReconciliationReviewChanged = (failure: Failure): boolean =>
  payrollPaymentReviewChanged(failure) ||
  [
    "payroll_payment_not_released",
    "payroll_payment_item_not_pending",
    "payroll_reconciliation_unavailable",
  ].includes(failure.code);
export const payrollPaymentReconciliationWasRejected = (failure: Failure): boolean =>
  payrollPaymentActionWasRejected(failure) ||
  payrollPaymentReconciliationReviewChanged(failure) ||
  [
    "invalid_payroll_payment_results",
    "invalid_payroll_payment_result",
    "duplicate_payment_transaction_reference",
  ].includes(failure.code);
