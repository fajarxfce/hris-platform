import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentBatchId } from "../entities/payroll-payment-batch";
import { payrollPaymentSnapshot } from "../entities/payroll-payment-snapshot";
import type { PayrollPaymentTransition } from "../entities/payroll-payment-transition";
import { canReadPayrollPayments } from "../policies/payroll-payment-read-policy";
import { canTransitionPayrollPayment } from "../policies/payroll-payment-transition-policy";
import type { PayrollPaymentRepository } from "../repositories/payroll-payment-repository";
export class ReviewPayrollPaymentTransition {
  constructor(private readonly payments: PayrollPaymentRepository) {}
  async execute(
    access: CompanyAccess,
    id: string,
    transition: PayrollPaymentTransition,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadPayrollPayments(access.permissions)) return failed("access_denied");
    if (!isUuid(id)) return failed("payroll_payment_batch_not_found");
    const result = await this.payments.review(
      access.companyId,
      id.toLowerCase() as PayrollPaymentBatchId,
      null,
      signal,
    );
    signal.throwIfAborted();
    if (
      result.ok &&
      !canTransitionPayrollPayment(access, payrollPaymentSnapshot(result.value), transition)
    )
      return failed("payroll_payment_action_unavailable");
    return result;
  }
}
