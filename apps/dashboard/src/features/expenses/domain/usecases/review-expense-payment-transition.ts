import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePaymentBatchId } from "../entities/expense-payment-batch";
import { expensePaymentSnapshot } from "../entities/expense-payment-snapshot";
import type { ExpensePaymentTransition } from "../entities/expense-payment-transition";
import { canReadExpensePayments } from "../policies/expense-payment-read-policy";
import { canTransitionExpensePayment } from "../policies/expense-payment-transition-policy";
import type { ExpensePaymentRepository } from "../repositories/expense-payment-repository";
export class ReviewExpensePaymentTransition {
  constructor(private readonly payments: ExpensePaymentRepository) {}
  async execute(
    access: CompanyAccess,
    id: string,
    transition: ExpensePaymentTransition,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadExpensePayments(access.permissions)) return failed("access_denied");
    if (!isUuid(id)) return failed("expense_payment_batch_not_found");
    const result = await this.payments.review(
      access.companyId,
      id.toLowerCase() as ExpensePaymentBatchId,
      null,
      signal,
    );
    signal.throwIfAborted();
    if (
      result.ok &&
      !canTransitionExpensePayment(access, expensePaymentSnapshot(result.value), transition)
    )
      return failed("expense_payment_action_unavailable");
    return result;
  }
}
