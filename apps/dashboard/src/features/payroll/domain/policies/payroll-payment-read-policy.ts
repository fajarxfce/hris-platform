import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { payrollPaymentBatchStatuses } from "../entities/payroll-payment-batch";
import type {
  PayrollPaymentSearch,
  PayrollPaymentSearchInput,
} from "../entities/payroll-payment-search";
export const canReadPayrollPayments = (permissions: readonly string[]): boolean =>
  permissions.includes("payroll.pay");
export function parsePayrollPaymentSearch(
  input: PayrollPaymentSearchInput,
): Result<PayrollPaymentSearch> {
  const status =
    input.status === null
      ? null
      : payrollPaymentBatchStatuses.find((value) => value === input.status);
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
    return failed("invalid_payroll_payment_page");
  return success(Object.freeze({ ...input, status, after: input.after?.toLowerCase() ?? null }));
}

export const isPayrollPaymentHistoryCursor = (value: string): boolean =>
  /^(?:0|[1-9]\d{0,2})$/u.test(value) && Number(value) <= 101;
