import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseClaimAction } from "../entities/expense-claim-action";
import type { ExpenseClaimActionSnapshot } from "../entities/expense-claim-action-snapshot";
import { prepareExpenseClaimAction } from "../policies/expense-claim-action-policy";
import type { ExpenseClaimRepository } from "../repositories/expense-claim-repository";
export class TransitionExpenseClaim {
  constructor(private readonly claims: ExpenseClaimRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    snapshot: ExpenseClaimActionSnapshot,
    action: ExpenseClaimAction,
    reason: string,
    submissionId: string | null,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const command = prepareExpenseClaimAction(
      access,
      operation,
      snapshot,
      action,
      reason,
      submissionId,
    );
    if (!command.ok) return Promise.resolve(command);
    return this.claims.transition(access.companyId, operation, command.value, signal);
  }
}
