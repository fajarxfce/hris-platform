import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentReviewAction } from "../entities/payroll-amendment-review-details";
import { canActOnAmendmentReview } from "../policies/payroll-amendment-review-policy";
import type { PayrollAmendmentReviewRepository } from "../repositories/payroll-amendment-review-repository";

export class LoadPayrollAmendmentReviewAction {
  constructor(private readonly reviews: PayrollAmendmentReviewRepository) {}
  async execute(
    access: CompanyAccess,
    id: string,
    action: PayrollAmendmentReviewAction,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canActOnAmendmentReview(access.permissions, action)) return failed("access_denied");
    if (!isUuid(id)) return failed("payroll_amendment_review_not_found");
    const result = await this.reviews.get(access.companyId, id.toLowerCase(), signal);
    signal.throwIfAborted();
    if (!result.ok) return result;
    if (!result.value.availableActions.includes(action))
      return failed("payroll_amendment_review_action_unavailable");
    return result;
  }
}
