import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollReviewAction } from "../entities/payroll-review-command";

import type { PayrollReviewDetails } from "../entities/payroll-review-details";
import type { PayrollReviewRepository } from "../repositories/payroll-review-repository";
export class LoadPayrollReviewAction {
  constructor(private readonly reviews: PayrollReviewRepository) {}
  async execute(
    access: CompanyAccess,
    id: string,
    action: PayrollReviewAction,
    signal: AbortSignal,
  ): Promise<Result<PayrollReviewDetails>> {
    signal.throwIfAborted();
    if (
      !access.permissions.includes(action === "WITHDRAW" ? "payroll.calculate" : "payroll.review")
    )
      return failed("access_denied");
    if (!isUuid(id)) return failed("payroll_review_not_found");
    const review = await this.reviews.get(access.companyId, id.toLowerCase(), signal);
    signal.throwIfAborted();
    return review.ok && !review.value.availableActions.includes(action)
      ? failed("payroll_review_action_unavailable")
      : review;
  }
}
