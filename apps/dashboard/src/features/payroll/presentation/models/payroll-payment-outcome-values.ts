import {
  companyDateTimeInput,
  parseCompanyDateTime,
} from "../../../../core/presentation/dates/company-date-time";
import type { PayrollPaymentOutcome } from "../../domain/entities/payroll-payment-outcome";
export type PayrollPaymentOutcomeValues = {
  status: string;
  reference: string;
  occurredAt: string;
  reason: string;
  confirmedNoTransfer: boolean;
};
export function payrollPaymentOutcomeValues(
  outcome: PayrollPaymentOutcome | null,
  timezone: string,
): PayrollPaymentOutcomeValues {
  return {
    status: outcome?.status ?? "",
    reference: outcome?.transactionReference ?? "",
    occurredAt: outcome ? companyDateTimeInput(outcome.occurredAt, timezone) : "",
    reason: outcome?.reason ?? "",
    confirmedNoTransfer: outcome?.confirmedNoTransfer ?? false,
  };
}
/** Convert company-local form text; result eligibility is validated by domain policy. */
export function parsePayrollPaymentOutcomeValues(
  input: PayrollPaymentOutcomeValues,
  itemId: string,
  timezone: string,
  original: PayrollPaymentOutcome | null,
): PayrollPaymentOutcome | null {
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
