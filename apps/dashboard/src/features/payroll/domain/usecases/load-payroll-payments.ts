import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentSearchInput } from "../entities/payroll-payment-search";
import {
  canReadPayrollPayments,
  parsePayrollPaymentSearch,
} from "../policies/payroll-payment-read-policy";
import type { PayrollPaymentRepository } from "../repositories/payroll-payment-repository";
export class LoadPayrollPayments {
  constructor(private readonly payments: PayrollPaymentRepository) {}
  execute(access: CompanyAccess, input: PayrollPaymentSearchInput, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadPayrollPayments(access.permissions))
      return Promise.resolve(failed("access_denied"));
    const search = parsePayrollPaymentSearch(input);
    if (!search.ok) return Promise.resolve(search);
    return this.payments.list(access.companyId, search.value, signal);
  }
}
