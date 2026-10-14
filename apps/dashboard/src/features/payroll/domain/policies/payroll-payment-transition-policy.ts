import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentSnapshot } from "../entities/payroll-payment-snapshot";
import {
  type PayrollPaymentTransition,
  payrollPaymentTransitions,
} from "../entities/payroll-payment-transition";
import type { PayrollPaymentTransitionCommand } from "../entities/payroll-payment-transition-command";
import { payrollPaymentEvidenceUnavailable } from "./payroll-payment-failure-policy";
import { canReadPayrollPayments } from "./payroll-payment-read-policy";
export const canTransitionPayrollPayment = (
  access: CompanyAccess,
  review: PayrollPaymentSnapshot,
  transition: PayrollPaymentTransition,
): boolean =>
  access.companyId === review.companyId &&
  canReadPayrollPayments(access.permissions) &&
  review.status === "PREPARED" &&
  review.availableActions.includes(transition);
export function preparePayrollPaymentTransition(
  access: CompanyAccess,
  operation: OperationId,
  review: PayrollPaymentSnapshot,
  transition: PayrollPaymentTransition,
  inputReason: string,
): Result<PayrollPaymentTransitionCommand> {
  if (access.companyId !== review.companyId || !canReadPayrollPayments(access.permissions))
    return failed("access_denied");
  if (!canTransitionPayrollPayment(access, review, transition))
    return failed("payroll_payment_action_unavailable");
  const reason = inputReason.trim();
  if (
    !payrollPaymentTransitions.includes(transition) ||
    !isUuid(operation) ||
    !isUuid(review.id) ||
    !Number.isSafeInteger(review.version) ||
    review.version < 0 ||
    review.version >= Number.MAX_SAFE_INTEGER ||
    reason.length < 1 ||
    reason.length > 1000
  )
    return failed("invalid_payroll_payment_action");
  return success(Object.freeze({ id: review.id, version: review.version, transition, reason }));
}
export const payrollPaymentReviewChanged = (failure: Failure): boolean =>
  [
    "stale_version",
    "payroll_payment_not_prepared",
    "payroll_payment_evidence_unavailable",
    "payroll_payment_batch_not_found",
    "payroll_payment_action_unavailable",
    "independent_payment_release_required",
  ].includes(failure.code);
export const payrollPaymentActionWasRejected = (failure: Failure): boolean =>
  payrollPaymentEvidenceUnavailable(failure) ||
  payrollPaymentReviewChanged(failure) ||
  [
    "invalid_payroll_payment_action",
    "data_conflict",
    "company_required",
    "csrf_invalid",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
