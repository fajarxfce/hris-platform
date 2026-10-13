import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePaymentSearchInput } from "../entities/expense-payment-search";
import {
  canReadExpensePayments,
  parseExpensePaymentSearch,
} from "../policies/expense-payment-read-policy";
import type { ExpensePaymentRepository } from "../repositories/expense-payment-repository";
export class LoadExpensePayments {
  constructor(private readonly payments: ExpensePaymentRepository) {}
  execute(access: CompanyAccess, input: ExpensePaymentSearchInput, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadExpensePayments(access.permissions))
      return Promise.resolve(failed("access_denied"));
    const search = parseExpensePaymentSearch(input);
    if (!search.ok) return Promise.resolve(search);
    return this.payments.list(access.companyId, search.value, signal);
  }
}
