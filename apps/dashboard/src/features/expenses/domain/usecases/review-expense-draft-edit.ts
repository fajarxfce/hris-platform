import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseClaimId } from "../entities/expense-claim";
import type { ExpenseDraftEditReview } from "../entities/expense-draft-edit-review";
import {
  canEditExpenseDraft,
  canManageExpenseDrafts,
  expenseDraftReferenceQuery,
} from "../policies/expense-draft-policy";
import type { ExpenseClaimRepository } from "../repositories/expense-claim-repository";
import type { ExpenseDraftingRepository } from "../repositories/expense-drafting-repository";
export class ReviewExpenseDraftEdit {
  constructor(
    private readonly claims: ExpenseClaimRepository,
    private readonly drafting: ExpenseDraftingRepository,
  ) {}
  async execute(
    access: CompanyAccess,
    id: string,
    signal: AbortSignal,
  ): Promise<Result<ExpenseDraftEditReview>> {
    signal.throwIfAborted();
    if (!canManageExpenseDrafts(access.permissions)) return failed("access_denied");
    if (!isUuid(id)) return failed("expense_claim_not_found");
    const read = await this.claims.review(
      access.companyId,
      id.toLowerCase() as ExpenseClaimId,
      null,
      signal,
    );
    signal.throwIfAborted();
    if (!read.ok) return read;
    const claim = read.value.claim;
    if (!canEditExpenseDraft(claim))
      return failed(
        claim.draftRevision >= 99 ? "expense_draft_revision_limit" : "expense_not_editable",
      );
    const references = await this.drafting.references(
      access.companyId,
      claim.employmentId,
      expenseDraftReferenceQuery(claim.draft.lines),
      signal,
    );
    signal.throwIfAborted();
    if (!references.ok) return references;
    return success(Object.freeze({ claim, references: references.value }));
  }
}
