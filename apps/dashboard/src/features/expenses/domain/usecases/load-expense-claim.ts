import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseClaimId } from "../entities/expense-claim";
import {
  canReadExpenseClaims,
  isExpenseHistoryCursor,
} from "../policies/expense-claim-read-policy";
import type { ExpenseClaimRepository } from "../repositories/expense-claim-repository";
export class LoadExpenseClaim {
  constructor(private readonly claims: ExpenseClaimRepository) {}
  execute(access: CompanyAccess, id: string, historyAfter: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadExpenseClaims(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("expense_claim_not_found"));
    if (historyAfter !== null && !isExpenseHistoryCursor(historyAfter))
      return Promise.resolve(failed("invalid_page"));
    return this.claims.review(
      access.companyId,
      id.toLowerCase() as ExpenseClaimId,
      historyAfter,
      signal,
    );
  }
}
