import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollReviewPreparation } from "../entities/payroll-review-preparation";
import type { PayrollReviewRepository } from "../repositories/payroll-review-repository";
export class LoadPayrollReviewSubmission {
  constructor(private readonly reviews: PayrollReviewRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollReviewPreparation>> {
    signal.throwIfAborted();
    if (!access.permissions.includes("payroll.calculate"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("payroll_run_not_found"));
    return this.reviews.prepare(access.companyId, id.toLowerCase(), signal);
  }
}
