import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePaymentOutcome } from "../entities/expense-payment-outcome";
import type { ExpenseReconciliationSnapshot } from "../entities/expense-reconciliation-snapshot";
import { prepareExpenseReconciliation } from "../policies/expense-reconciliation-policy";
import type { ExpensePaymentRepository } from "../repositories/expense-payment-repository";
export class ReconcileExpensePayment {
  constructor(private readonly payments: ExpensePaymentRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    snapshot: ExpenseReconciliationSnapshot,
    results: readonly ExpensePaymentOutcome[],
    reason: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const prepared = prepareExpenseReconciliation(access, operation, snapshot, results, reason);
    return prepared.ok
      ? this.payments.reconcile(access.companyId, operation, prepared.value, signal)
      : Promise.resolve(prepared);
  }
}
