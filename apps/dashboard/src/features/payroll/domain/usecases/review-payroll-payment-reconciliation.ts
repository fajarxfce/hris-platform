import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentBatchId } from "../entities/payroll-payment-batch";
import { payrollPaymentReconciliationSnapshot } from "../entities/payroll-payment-reconciliation-snapshot";
import { canReadPayrollPayments } from "../policies/payroll-payment-read-policy";
import { canReconcilePayrollPayment } from "../policies/payroll-payment-reconciliation-policy";
import type { PayrollPaymentRepository } from "../repositories/payroll-payment-repository";
export class ReviewPayrollPaymentReconciliation {
  constructor(private readonly payments: PayrollPaymentRepository) {}
  async execute(access: CompanyAccess, id: string, signal: AbortSignal) {
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
      !canReconcilePayrollPayment(access, payrollPaymentReconciliationSnapshot(result.value))
    )
      return failed("payroll_reconciliation_unavailable");
    return result;
  }
}
