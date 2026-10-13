import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseDecision } from "../entities/expense-decision";
import type { ExpenseDecisionSnapshot } from "../entities/expense-decision-snapshot";
import { prepareExpenseDecision } from "../policies/expense-decision-policy";
import type { ExpenseClaimRepository } from "../repositories/expense-claim-repository";

export class DecideExpenseSubmission {
  constructor(private readonly claims: ExpenseClaimRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    review: ExpenseDecisionSnapshot,
    decision: ExpenseDecision,
    reason: string,
    acknowledgeDuplicates: boolean,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const prepared = prepareExpenseDecision(
      access,
      operation,
      review,
      decision,
      reason,
      acknowledgeDuplicates,
    );
    return prepared.ok
      ? this.claims.decide(access.companyId, operation, prepared.value, signal)
      : Promise.resolve(prepared);
  }
}
