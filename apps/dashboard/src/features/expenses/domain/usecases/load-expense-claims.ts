import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseClaimSearchInput } from "../entities/expense-claim-search";
import {
  canBrowseCompanyExpenses,
  canReadExpenseClaims,
  parseExpenseClaimSearch,
} from "../policies/expense-claim-read-policy";
import type { ExpenseClaimRepository } from "../repositories/expense-claim-repository";
export class LoadExpenseClaims {
  constructor(private readonly claims: ExpenseClaimRepository) {}
  execute(access: CompanyAccess, input: ExpenseClaimSearchInput, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadExpenseClaims(access.permissions)) return Promise.resolve(failed("access_denied"));
    const parsed = parseExpenseClaimSearch(input);
    if (!parsed.ok) return Promise.resolve(parsed);
    if (!canBrowseCompanyExpenses(access.permissions) && parsed.value.employmentId === null)
      return Promise.resolve(failed("employee_scope_required"));
    return this.claims.list(access.companyId, parsed.value, signal);
  }
}
