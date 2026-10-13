import {
  companyDateTimeInput,
  parseCompanyDateTime,
} from "../../../../core/presentation/dates/company-date-time";
import type { ExpensePaymentOutcome } from "../../domain/entities/expense-payment-outcome";
export type ExpenseOutcomeValues = {
  status: string;
  reference: string;
  occurredAt: string;
  reason: string;
  confirmedNoTransfer: boolean;
};
export function expenseOutcomeValues(
  outcome: ExpensePaymentOutcome | null,
  timezone: string,
): ExpenseOutcomeValues {
  return {
    status: outcome?.status ?? "",
    reference: outcome?.transactionReference ?? "",
    occurredAt: outcome ? companyDateTimeInput(outcome.occurredAt, timezone) : "",
    reason: outcome?.reason ?? "",
    confirmedNoTransfer: outcome?.confirmedNoTransfer ?? false,
  };
}
/** Convert company-local form text; result eligibility is validated by domain policy. */
export function parseExpenseOutcomeValues(
  input: ExpenseOutcomeValues,
  itemId: string,
  timezone: string,
  original: ExpensePaymentOutcome | null,
): ExpensePaymentOutcome | null {
  const occurredAt = parseCompanyDateTime(input.occurredAt, timezone, original?.occurredAt ?? null);
  if (occurredAt === null || !["SUCCEEDED", "FAILED"].includes(input.status)) return null;
  return {
    itemId,
    status: input.status === "SUCCEEDED" ? "SUCCEEDED" : "FAILED",
    transactionReference: input.status === "SUCCEEDED" ? input.reference : null,
    occurredAt,
    reason: input.reason,
    confirmedNoTransfer: input.status === "FAILED" && input.confirmedNoTransfer,
  };
}
