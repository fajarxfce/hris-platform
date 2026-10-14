import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPayableSearch } from "../entities/payroll-payable-search";
import {
  canReadPayrollPayments,
  parsePayrollPaymentSearch,
} from "../policies/payroll-payment-read-policy";
import type { PayrollPaymentRepository } from "../repositories/payroll-payment-repository";
export class LoadPayrollPayables {
  constructor(private readonly payments: PayrollPaymentRepository) {}
  execute(access: CompanyAccess, input: PayrollPayableSearch, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadPayrollPayments(access.permissions))
      return Promise.resolve(failed("access_denied"));
    const search = parsePayrollPaymentSearch({ ...input, status: null });
    if (!search.ok) return Promise.resolve(search);
    return this.payments.payables(access.companyId, search.value, signal);
  }
}
