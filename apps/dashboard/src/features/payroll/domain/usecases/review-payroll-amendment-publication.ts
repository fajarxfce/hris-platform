import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentPublicationPreparation } from "../entities/payroll-amendment-publication-preparation";
import {
  amendmentPublicationStartFailure,
  canPublishPayrollAmendments,
} from "../policies/payroll-amendment-publication-policy";
import type { PayrollAmendmentPublicationRepository } from "../repositories/payroll-amendment-publication-repository";
import type { PayrollAmendmentRepository } from "../repositories/payroll-amendment-repository";
import type { PayrollAmendmentReviewRepository } from "../repositories/payroll-amendment-review-repository";

export class ReviewPayrollAmendmentPublication {
  constructor(
    private readonly amendments: PayrollAmendmentRepository,
    private readonly reviews: PayrollAmendmentReviewRepository,
    private readonly publications: PayrollAmendmentPublicationRepository,
  ) {}
  async execute(
    access: CompanyAccess,
    id: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentPublicationPreparation>> {
    signal.throwIfAborted();
    if (!canPublishPayrollAmendments(access.permissions)) return failed("access_denied");
    if (!isUuid(id)) return failed("payroll_amendment_not_found");
    const amendmentId = id.toLowerCase(),
      acquired = await this.amendments.get(access.companyId, amendmentId, signal);
    signal.throwIfAborted();
    if (!acquired.ok) return acquired;
    const current = acquired.value,
      amendment = current.amendment.summary;
    if (
      amendment.status !== "APPROVED" ||
      current.latestReview?.status !== "APPROVED" ||
      current.latestCalculation?.status !== "SUCCEEDED"
    )
      return failed("payroll_amendment_approved_review_required");
    const reviewed = await this.reviews.get(access.companyId, current.latestReview.id, signal);
    signal.throwIfAborted();
    if (!reviewed.ok) return reviewed;
    const details = reviewed.value;
    if (
      details.amendment.id !== amendmentId ||
      details.amendment.version !== amendment.version ||
      details.review.version !== current.latestReview.version ||
      details.review.status !== "APPROVED" ||
      details.approval.status !== "APPROVED" ||
      details.calculation.id !== current.latestCalculation.id ||
      details.review.id !== amendment.latestReviewId ||
      details.calculation.id !== amendment.latestCalculationId
    )
      return failed("payroll_amendment_publication_changed");
    const found = await this.publications.list(access.companyId, amendmentId, signal);
    signal.throwIfAborted();
    if (!found.ok) return found;
    const latest = found.value.at(-1);
    if (
      (latest?.publication.id ?? null) !== amendment.latestPublicationId ||
      (latest && latest.amendment.version !== amendment.version)
    )
      return failed("payroll_amendment_publication_changed");
    const failure = amendmentPublicationStartFailure(amendment, found.value);
    if (failure) return { ok: false, failure };
    return success(
      Object.freeze({
        amendment,
        review: details.review,
        calculation: details.calculation,
        approvalVersion: details.approval.version,
        number: found.value.length + 1,
      }),
    );
  }
}
