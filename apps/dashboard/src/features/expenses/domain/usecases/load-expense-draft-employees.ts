import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canManageExpenseDrafts } from "../policies/expense-draft-policy";
import type { ExpenseDraftingRepository } from "../repositories/expense-drafting-repository";
export class LoadExpenseDraftEmployees {
  constructor(private readonly drafting: ExpenseDraftingRepository) {}
  execute(access: CompanyAccess, query: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageExpenseDrafts(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (
      query.trim().length > 120 ||
      (after !== null && (!after.trim().length || after.length > 32))
    )
      return Promise.resolve(failed("invalid_page"));
    return this.drafting.employees(access.companyId, query.trim(), after, signal);
  }
}
