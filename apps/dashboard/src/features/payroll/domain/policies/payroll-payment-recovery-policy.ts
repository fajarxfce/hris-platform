import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentRecoveryCommand } from "../entities/payroll-payment-recovery-command";
import type { PayrollPaymentRecoveryInput } from "../entities/payroll-payment-recovery-input";
import type { PayrollPaymentRecoverySnapshot } from "../entities/payroll-payment-recovery-snapshot";
import { payrollPaymentAmountMinor } from "./payroll-payment-amount";
import { payrollPaymentEvidenceUnavailable } from "./payroll-payment-failure-policy";
import { canReadPayrollPayments } from "./payroll-payment-read-policy";
import { payrollSettlementAmountMinor } from "./payroll-settlement-amount";

/** Review bounds are observed facts. The API checks live ownership, balance and its own clock. */
export function preparePayrollPaymentRecovery(
  access: CompanyAccess,
  operation: OperationId,
  id: string,
  snapshot: PayrollPaymentRecoverySnapshot,
  input: PayrollPaymentRecoveryInput,
): Result<PayrollPaymentRecoveryCommand> {
  if (access.companyId !== snapshot.companyId || !canReadPayrollPayments(access.permissions))
    return failed("access_denied");
  if (!snapshot.canConfirm) return failed("payroll_payment_recovery_unavailable");
  const amount = input.amount.trim(),
    reference = input.transactionReference.trim(),
    reason = input.reason.trim();
  const minor = payrollPaymentAmountMinor(amount),
    maximum = payrollSettlementAmountMinor(snapshot.recoverable),
    earliest = utcInstantMicroseconds(snapshot.earliestConfirmationAt),
    occurred = utcInstantMicroseconds(input.occurredAt);
  if (
    !isUuid(operation) ||
    !isUuid(id) ||
    !isUuid(snapshot.assessmentId) ||
    !Number.isSafeInteger(snapshot.assessmentRevision) ||
    snapshot.assessmentRevision < 0 ||
    !Number.isSafeInteger(snapshot.paymentVersion) ||
    snapshot.paymentVersion < 0 ||
    snapshot.paymentVersion < snapshot.assessmentRevision ||
    minor === null ||
    minor <= 0n ||
    maximum === null ||
    minor > maximum ||
    occurred === null ||
    earliest === null ||
    occurred < earliest ||
    !/^[A-Za-z0-9][A-Za-z0-9._:/ -]{0,99}$/u.test(reference) ||
    reason.length < 1 ||
    reason.length > 1000
  )
    return failed("invalid_payroll_payment_recovery");
  return success(
    Object.freeze({
      id: id.toLowerCase(),
      assessmentId: snapshot.assessmentId.toLowerCase(),
      assessmentRevision: snapshot.assessmentRevision,
      paymentVersion: snapshot.paymentVersion,
      amount,
      transactionReference: reference,
      occurredAt: input.occurredAt,
      reason,
    }),
  );
}

export const payrollPaymentRecoveryEvidenceUnavailable = (failure: Failure): boolean =>
  payrollPaymentEvidenceUnavailable(failure) ||
  ["payroll_payslip_not_found", "payroll_self_recovery_forbidden"].includes(failure.code);

export const payrollPaymentRecoveryReviewChanged = (failure: Failure): boolean =>
  [
    "payroll_payment_balance_changed",
    "payroll_payment_recovery_exceeds_balance",
    "payroll_payment_recovery_limit",
    "payroll_amendment_payment_pending",
    "payroll_payment_recovery_unavailable",
  ].includes(failure.code);

export const payrollPaymentRecoveryWasRejected = (failure: Failure): boolean =>
  payrollPaymentRecoveryEvidenceUnavailable(failure) ||
  payrollPaymentRecoveryReviewChanged(failure) ||
  [
    "invalid_payroll_payment_recovery",
    "payroll_payment_recovery_exists",
    "data_conflict",
    "company_required",
    "csrf_invalid",
    "client_version_required",
    "invalid_client_version",
    "request_rate_limited",
  ].includes(failure.code);
