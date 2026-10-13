import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canManageExpenseDrafts } from "../policies/expense-draft-policy";
import type { ExpenseDraftingRepository } from "../repositories/expense-drafting-repository";
export class LoadExpenseDraftReceipts {
  constructor(private readonly drafting: ExpenseDraftingRepository) {}
  execute(access: CompanyAccess, employmentId: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageExpenseDrafts(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(employmentId) || (after !== null && !isUuid(after)))
      return Promise.resolve(failed("invalid_page"));
    return this.drafting.receipts(
      access.companyId,
      employmentId.toLowerCase(),
      after?.toLowerCase() ?? null,
      signal,
    );
  }
}
