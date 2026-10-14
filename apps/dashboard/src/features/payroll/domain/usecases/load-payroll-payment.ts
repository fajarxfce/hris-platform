import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentBatchId } from "../entities/payroll-payment-batch";
import {
  canReadPayrollPayments,
  isPayrollPaymentHistoryCursor,
} from "../policies/payroll-payment-read-policy";
import type { PayrollPaymentRepository } from "../repositories/payroll-payment-repository";
export class LoadPayrollPayment {
  constructor(private readonly payments: PayrollPaymentRepository) {}
  execute(access: CompanyAccess, id: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadPayrollPayments(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("payroll_payment_batch_not_found"));
    if (after !== null && !isPayrollPaymentHistoryCursor(after))
      return Promise.resolve(failed("invalid_page"));
    return this.payments.review(
      access.companyId,
      id.toLowerCase() as PayrollPaymentBatchId,
      after,
      signal,
    );
  }
}
