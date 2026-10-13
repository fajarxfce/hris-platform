import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseClaimId } from "../entities/expense-claim";
import type { ExpenseClaimAction } from "../entities/expense-claim-action";
import type { ExpenseClaimActionReview } from "../entities/expense-claim-action-review";
import { expenseClaimActionSnapshot } from "../entities/expense-claim-action-snapshot";
import { availableExpenseClaimActions } from "../policies/expense-claim-action-policy";
import {
  canManageExpenseDrafts,
  expenseDraftReferenceQuery,
} from "../policies/expense-draft-policy";
import type { ExpenseClaimRepository } from "../repositories/expense-claim-repository";
import type { ExpenseDraftingRepository } from "../repositories/expense-drafting-repository";
export class ReviewExpenseClaimAction {
  constructor(
    private readonly claims: ExpenseClaimRepository,
    private readonly drafting: ExpenseDraftingRepository,
  ) {}
  async execute(
    access: CompanyAccess,
    id: string,
    action: ExpenseClaimAction,
    signal: AbortSignal,
  ): Promise<Result<ExpenseClaimActionReview>> {
    signal.throwIfAborted();
    if (!canManageExpenseDrafts(access.permissions)) return failed("access_denied");
    if (!isUuid(id)) return failed("expense_claim_not_found");
    const result = await this.claims.review(
      access.companyId,
      id.toLowerCase() as ExpenseClaimId,
      null,
      signal,
    );
    signal.throwIfAborted();
    if (!result.ok) return result;
    const claim = result.value.claim;
    if (!availableExpenseClaimActions(access, expenseClaimActionSnapshot(claim)).includes(action))
      return failed("expense_claim_action_unavailable");
    if (action !== "SUBMIT") return success(Object.freeze({ claim, references: null }));
    const references = await this.drafting.references(
      access.companyId,
      claim.employmentId,
      expenseDraftReferenceQuery(claim.draft.lines),
      signal,
    );
    signal.throwIfAborted();
    return references.ok
      ? success(Object.freeze({ claim, references: references.value }))
      : references;
  }
}
