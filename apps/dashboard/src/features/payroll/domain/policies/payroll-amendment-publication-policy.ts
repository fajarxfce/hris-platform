import type { Failure } from "../../../../core/domain/result";
import type { PayrollAmendmentSummary } from "../entities/payroll-amendment";
import type { PayrollAmendmentPublicationDetails } from "../entities/payroll-amendment-publication-details";
import {
  amendmentReviewAccessLost,
  amendmentReviewChanged,
  amendmentReviewCommandRejected,
} from "./payroll-amendment-review-policy";

export const canPublishPayrollAmendments = (permissions: readonly string[]): boolean =>
  permissions.includes("payroll.amend.publish");
export function amendmentPublicationStartFailure(
  amendment: PayrollAmendmentSummary,
  attempts: readonly PayrollAmendmentPublicationDetails[],
): Failure | null {
  if (amendment.status !== "APPROVED")
    return { code: "payroll_amendment_approved_review_required", fields: {}, parameters: {} };
  if (attempts.length >= 8)
    return { code: "payroll_amendment_publication_limit", fields: {}, parameters: {} };
  if (attempts.at(-1) && attempts.at(-1)?.publication.status !== "STOPPED")
    return { code: "payroll_amendment_publication_active", fields: {}, parameters: {} };
  if (amendment.version > 996)
    return { code: "payroll_amendment_revision_limit", fields: {}, parameters: {} };
  return null;
}
export const amendmentPublicationAccessLost = (failure: Failure): boolean =>
  amendmentReviewAccessLost(failure) || failure.code === "payroll_amendment_publication_not_found";
export const amendmentPublicationChanged = (failure: Failure): boolean =>
  amendmentReviewChanged(failure) ||
  [
    "payroll_amendment_approved_review_required",
    "payroll_amendment_publication_active",
    "payroll_amendment_publication_limit",
    "payroll_amendment_publication_changed",
    "payroll_amendment_publication_obsolete",
    "payroll_amendment_recovery_unavailable",
    "payroll_amendment_payment_pending",
    "job_queue_full",
  ].includes(failure.code);
export const amendmentPublicationCommandRejected = (failure: Failure): boolean =>
  amendmentReviewCommandRejected(failure) ||
  amendmentPublicationAccessLost(failure) ||
  amendmentPublicationChanged(failure) ||
  ["invalid_payroll_amendment_publication", "invalid_job_version"].includes(failure.code);
