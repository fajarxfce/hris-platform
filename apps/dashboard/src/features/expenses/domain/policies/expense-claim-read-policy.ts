import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { expenseClaimStatuses } from "../entities/expense-claim";
import type { ExpenseClaimSearch, ExpenseClaimSearchInput } from "../entities/expense-claim-search";
export const canBrowseCompanyExpenses = (permissions: readonly string[]): boolean =>
  permissions.some((permission) => ["expenses.read", "expenses.manage"].includes(permission));
export const canReadExpenseClaims = (permissions: readonly string[]): boolean =>
  canBrowseCompanyExpenses(permissions) ||
  permissions.some((permission) =>
    ["expenses.self.manage", "expenses.team.read"].includes(permission),
  );
export const canReadExpenseSubmissions = (permissions: readonly string[]): boolean =>
  canReadExpenseClaims(permissions) ||
  permissions.some((permission) =>
    ["expenses.approve", "expenses.team.approve"].includes(permission),
  );
export const isExpenseHistoryCursor = (value: string): boolean =>
  /^(?:0|[1-9]\d{0,15})$/u.test(value) && Number.isSafeInteger(Number(value));
export function parseExpenseClaimSearch(
  input: ExpenseClaimSearchInput,
): Result<ExpenseClaimSearch> {
  const status =
    input.status === null ? null : expenseClaimStatuses.find((value) => value === input.status);
  if (
    !isCalendarDate(input.from) ||
    !isCalendarDate(input.until) ||
    input.from < "1900-01-01" ||
    input.until > "2200-12-31" ||
    input.from > input.until ||
    (Date.parse(`${input.until}T00:00:00Z`) - Date.parse(`${input.from}T00:00:00Z`)) / 86_400_000 >
      365 ||
    status === undefined ||
    (input.employmentId !== null && !isUuid(input.employmentId)) ||
    (input.after !== null && !isUuid(input.after))
  )
    return failed("invalid_expense_page");
  return success(
    Object.freeze({
      ...input,
      status,
      employmentId: input.employmentId?.toLowerCase() ?? null,
      after: input.after?.toLowerCase() ?? null,
    }),
  );
}
