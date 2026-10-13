import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePaymentPreparation } from "../entities/expense-payment-preparation";
import { prepareExpensePaymentCommand } from "../policies/expense-payment-preparation-policy";
import type { ExpensePaymentRepository } from "../repositories/expense-payment-repository";
export class PrepareExpensePayment {
  constructor(private readonly payments: ExpensePaymentRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    id: string,
    review: ExpensePaymentPreparation,
    itemIds: readonly string[],
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const command = prepareExpensePaymentCommand(access, operation, id, review, itemIds);
    if (!command.ok) return Promise.resolve(command);
    return this.payments.prepare(access.companyId, operation, command.value, signal);
  }
}
