import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePolicyId } from "../entities/expense-policy-definition";
import { canManageExpensePolicies } from "../policies/expense-read-policy";
import type { ExpensePolicyRepository } from "../repositories/expense-policy-repository";

export class LoadExpensePolicy {
  constructor(private readonly policies: ExpensePolicyRepository) {}
  execute(access: CompanyAccess, id: string, historyAfter: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageExpensePolicies(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("expense_category_not_found"));
    if (
      historyAfter !== null &&
      (!/^(?:0|[1-9]\d{0,15})$/u.test(historyAfter) ||
        !Number.isSafeInteger(Number(historyAfter)) ||
        Number(historyAfter) > 999)
    )
      return Promise.resolve(failed("invalid_page"));
    return this.policies.get(
      access.companyId,
      id.toLowerCase() as ExpensePolicyId,
      historyAfter,
      signal,
    );
  }
}
