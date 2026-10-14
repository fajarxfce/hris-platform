import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollReviewDetails } from "../entities/payroll-review-details";
import { canReadPayrollPeriods } from "../policies/payroll-period-policy";
import type { PayrollReviewRepository } from "../repositories/payroll-review-repository";
export class LoadPayrollReview {
  constructor(private readonly reviews: PayrollReviewRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollReviewDetails>> {
    signal.throwIfAborted();
    if (!canReadPayrollPeriods(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("payroll_review_not_found"));
    return this.reviews.get(access.companyId, id.toLowerCase(), signal);
  }
}
