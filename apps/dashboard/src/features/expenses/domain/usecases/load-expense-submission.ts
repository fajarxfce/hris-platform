import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseSubmissionId } from "../entities/expense-submission-summary";
import { canReadExpenseSubmissions } from "../policies/expense-claim-read-policy";
import type { ExpenseClaimRepository } from "../repositories/expense-claim-repository";
export class LoadExpenseSubmission {
  constructor(private readonly claims: ExpenseClaimRepository) {}
  execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadExpenseSubmissions(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("expense_submission_not_found"));
    return this.claims.submission(
      access.companyId,
      id.toLowerCase() as ExpenseSubmissionId,
      signal,
    );
  }
}
