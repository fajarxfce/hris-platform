import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePaymentBatchId } from "../entities/expense-payment-batch";
import { expenseReconciliationSnapshot } from "../entities/expense-reconciliation-snapshot";
import { canReadExpensePayments } from "../policies/expense-payment-read-policy";
import { canReconcileExpensePayment } from "../policies/expense-reconciliation-policy";
import type { ExpensePaymentRepository } from "../repositories/expense-payment-repository";
export class ReviewExpenseReconciliation {
  constructor(private readonly payments: ExpensePaymentRepository) {}
  async execute(access: CompanyAccess, id: string, signal: AbortSignal) {
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
      !canReconcileExpensePayment(access, expenseReconciliationSnapshot(result.value))
    )
      return failed("expense_reconciliation_unavailable");
    return result;
  }
}
