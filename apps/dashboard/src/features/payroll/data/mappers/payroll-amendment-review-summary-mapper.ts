import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { PayrollAmendmentReviewSummary } from "../../domain/entities/payroll-amendment";
import type { PayrollAmendmentReviewSummaryDto } from "../models/payroll-amendment-review-dto";

export function toPayrollAmendmentReviewSummary(
  raw: PayrollAmendmentReviewSummaryDto,
): PayrollAmendmentReviewSummary {
  try {
    new Intl.DateTimeFormat("en", { timeZone: raw.timezone });
  } catch {
    throw new InvalidHttpResponseError();
  }
  return Object.freeze({
    ...raw,
    id: raw.id.toLowerCase(),
    amendmentId: raw.amendmentId.toLowerCase(),
    calculationId: raw.calculationId.toLowerCase(),
    approvalId: raw.approvalId.toLowerCase(),
    submittedBy: raw.submittedBy.toLowerCase(),
  });
}
