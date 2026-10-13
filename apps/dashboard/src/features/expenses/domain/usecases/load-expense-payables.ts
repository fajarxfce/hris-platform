import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePayableSearch } from "../entities/expense-payable-search";
import {
  canReadExpensePayments,
  parseExpensePaymentSearch,
} from "../policies/expense-payment-read-policy";
import type { ExpensePaymentRepository } from "../repositories/expense-payment-repository";
export class LoadExpensePayables {
  constructor(private readonly payments: ExpensePaymentRepository) {}
  execute(access: CompanyAccess, input: ExpensePayableSearch, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadExpensePayments(access.permissions))
      return Promise.resolve(failed("access_denied"));
    const search = parseExpensePaymentSearch({ ...input, status: null });
    if (!search.ok) return Promise.resolve(search);
    return this.payments.payables(access.companyId, search.value, signal);
  }
}
