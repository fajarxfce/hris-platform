import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseDraftChange } from "../entities/expense-draft-change";
import {
  canManageExpenseDrafts,
  normalizeExpenseDraft,
  validateExpenseDraft,
} from "../policies/expense-draft-policy";
import type { ExpenseClaimRepository } from "../repositories/expense-claim-repository";
export class SaveExpenseDraft {
  constructor(private readonly claims: ExpenseClaimRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: ExpenseDraftChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManageExpenseDrafts(access.permissions) || input.companyId !== access.companyId)
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_expense_draft"));
    const change = normalizeExpenseDraft(input),
      failure = validateExpenseDraft(change);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.claims.saveDraft(access.companyId, operation, change, signal);
  }
}
