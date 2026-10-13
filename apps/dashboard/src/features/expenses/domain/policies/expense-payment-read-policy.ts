import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { expensePaymentBatchStatuses } from "../entities/expense-payment-batch";
import type {
  ExpensePaymentSearch,
  ExpensePaymentSearchInput,
} from "../entities/expense-payment-search";
export const canReadExpensePayments = (permissions: readonly string[]): boolean =>
  permissions.includes("expenses.pay");
export function parseExpensePaymentSearch(
  input: ExpensePaymentSearchInput,
): Result<ExpensePaymentSearch> {
  const status =
    input.status === null
      ? null
      : expensePaymentBatchStatuses.find((value) => value === input.status);
  if (
    !isCalendarDate(input.from) ||
    !isCalendarDate(input.until) ||
    input.from < "1900-01-01" ||
    input.until > "2200-12-31" ||
    input.from > input.until ||
    (Date.parse(`${input.until}T00:00:00Z`) - Date.parse(`${input.from}T00:00:00Z`)) / 86_400_000 >
      365 ||
    status === undefined ||
    (input.after !== null && !isUuid(input.after))
  )
    return failed("invalid_expense_page");
  return success(Object.freeze({ ...input, status, after: input.after?.toLowerCase() ?? null }));
}
