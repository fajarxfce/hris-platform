import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentReviews } from "../entities/payroll-amendment-review-details";
import { canReadPayrollAmendments } from "../policies/payroll-amendment-access-policy";
import type { PayrollAmendmentRepository } from "../repositories/payroll-amendment-repository";
import type { PayrollAmendmentReviewRepository } from "../repositories/payroll-amendment-review-repository";

export class LoadPayrollAmendmentReviews {
  constructor(
    private readonly amendments: PayrollAmendmentRepository,
    private readonly reviews: PayrollAmendmentReviewRepository,
  ) {}
  async execute(
    access: CompanyAccess,
    amendment: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentReviews>> {
    signal.throwIfAborted();
    if (!canReadPayrollAmendments(access.permissions)) return failed("access_denied");
    if (!isUuid(amendment)) return failed("payroll_amendment_not_found");
    const id = amendment.toLowerCase(),
      current = await this.amendments.get(access.companyId, id, signal);
    signal.throwIfAborted();
    if (!current.ok) return current;
    const found = await this.reviews.list(access.companyId, id, signal);
    signal.throwIfAborted();
    if (!found.ok) return found;
    const expected = current.value.latestReview,
      latest = found.value.at(-1);
    if (
      (expected?.id ?? null) !== (latest?.id ?? null) ||
      (expected &&
        latest &&
        (expected.version !== latest.version || expected.status !== latest.status))
    )
      return failed("payroll_amendment_review_not_current");
    return success(Object.freeze({ current: current.value, reviews: found.value }));
  }
}
