import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canManageExpenseDrafts } from "../policies/expense-draft-policy";
import type { ExpensePolicyRepository } from "../repositories/expense-policy-repository";
export class LoadExpenseCategories {
  constructor(private readonly policies: ExpensePolicyRepository) {}
  execute(access: CompanyAccess, asOf: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageExpenseDrafts(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (
      !isCalendarDate(asOf) ||
      asOf < "1900-01-01" ||
      asOf > "2200-12-31" ||
      (after !== null && !/^[A-Z][A-Z0-9_]{1,31}$/u.test(after))
    )
      return Promise.resolve(failed("invalid_page"));
    return this.policies.categories(access.companyId, asOf, after, signal);
  }
}
