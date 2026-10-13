import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { expenseDecisionSnapshot } from "../entities/expense-decision-snapshot";
import type { ExpenseSubmissionId } from "../entities/expense-submission-summary";
import { canDecideExpenseSubmission, canReviewExpenses } from "../policies/expense-decision-policy";
import type { ExpenseClaimRepository } from "../repositories/expense-claim-repository";

export class ReviewExpenseDecision {
  constructor(private readonly claims: ExpenseClaimRepository) {}
  async execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReviewExpenses(access.permissions)) return failed("access_denied");
    if (!isUuid(id)) return failed("expense_submission_not_found");
    const result = await this.claims.submission(
      access.companyId,
      id.toLowerCase() as ExpenseSubmissionId,
      signal,
    );
    signal.throwIfAborted();
    if (result.ok && !canDecideExpenseSubmission(access, expenseDecisionSnapshot(result.value)))
      return failed("expense_decision_unavailable");
    return result;
  }
}
