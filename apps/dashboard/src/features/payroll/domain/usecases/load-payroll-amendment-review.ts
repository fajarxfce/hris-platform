import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadPayrollAmendments } from "../policies/payroll-amendment-access-policy";
import type { PayrollAmendmentReviewRepository } from "../repositories/payroll-amendment-review-repository";

export class LoadPayrollAmendmentReview {
  constructor(private readonly reviews: PayrollAmendmentReviewRepository) {}
  async execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadPayrollAmendments(access.permissions)) return failed("access_denied");
    if (!isUuid(id)) return failed("payroll_amendment_review_not_found");
    const result = await this.reviews.get(access.companyId, id.toLowerCase(), signal);
    signal.throwIfAborted();
    return result;
  }
}
