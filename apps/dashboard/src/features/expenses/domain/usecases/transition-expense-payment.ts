import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePaymentSnapshot } from "../entities/expense-payment-snapshot";
import type { ExpensePaymentTransition } from "../entities/expense-payment-transition";
import { prepareExpensePaymentTransition } from "../policies/expense-payment-transition-policy";
import type { ExpensePaymentRepository } from "../repositories/expense-payment-repository";
export class TransitionExpensePayment {
  constructor(private readonly payments: ExpensePaymentRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    snapshot: ExpensePaymentSnapshot,
    transition: ExpensePaymentTransition,
    reason: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const prepared = prepareExpensePaymentTransition(
      access,
      operation,
      snapshot,
      transition,
      reason,
    );
    return prepared.ok
      ? this.payments.transition(access.companyId, operation, prepared.value, signal)
      : Promise.resolve(prepared);
  }
}
