import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePaymentBatchId } from "../entities/expense-payment-batch";
import { isExpenseHistoryCursor } from "../policies/expense-claim-read-policy";
import { canReadExpensePayments } from "../policies/expense-payment-read-policy";
import type { ExpensePaymentRepository } from "../repositories/expense-payment-repository";
export class LoadExpensePayment {
  constructor(private readonly payments: ExpensePaymentRepository) {}
  execute(access: CompanyAccess, id: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadExpensePayments(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("expense_payment_batch_not_found"));
    if (after !== null && !isExpenseHistoryCursor(after))
      return Promise.resolve(failed("invalid_page"));
    return this.payments.review(
      access.companyId,
      id.toLowerCase() as ExpensePaymentBatchId,
      after,
      signal,
    );
  }
}
