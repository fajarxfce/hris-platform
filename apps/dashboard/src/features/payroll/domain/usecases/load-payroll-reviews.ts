import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollReviews } from "../entities/payroll-review-preparation";
import { canReadPayrollPeriods } from "../policies/payroll-period-policy";
import type { PayrollReviewRepository } from "../repositories/payroll-review-repository";
export class LoadPayrollReviews {
  constructor(private readonly reviews: PayrollReviewRepository) {}
  async execute(
    access: CompanyAccess,
    run: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollReviews>> {
    signal.throwIfAborted();
    if (!canReadPayrollPeriods(access.permissions)) return failed("access_denied");
    if (!isUuid(run)) return failed("payroll_run_not_found");
    const preparation = await this.reviews.prepare(access.companyId, run.toLowerCase(), signal);
    signal.throwIfAborted();
    if (!preparation.ok) return preparation;
    const history = await this.reviews.list(access.companyId, run.toLowerCase(), signal);
    signal.throwIfAborted();
    return history.ok
      ? success(Object.freeze({ preparation: preparation.value, reviews: history.value }))
      : history;
  }
}
