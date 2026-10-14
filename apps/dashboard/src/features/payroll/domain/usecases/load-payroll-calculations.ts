import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollCalculations } from "../entities/payroll-calculation-review";
import { canReadPayrollPeriods } from "../policies/payroll-period-policy";
import type { PayrollCalculationRepository } from "../repositories/payroll-calculation-repository";
export class LoadPayrollCalculations {
  constructor(private readonly calculations: PayrollCalculationRepository) {}
  async execute(
    access: CompanyAccess,
    id: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollCalculations>> {
    signal.throwIfAborted();
    if (!canReadPayrollPeriods(access.permissions)) return failed("access_denied");
    if (!isUuid(id)) return failed("payroll_period_not_found");
    const review = await this.calculations.review(access.companyId, id.toLowerCase(), signal);
    signal.throwIfAborted();
    if (!review.ok) return review;
    const runs = await this.calculations.runs(access.companyId, id.toLowerCase(), signal);
    signal.throwIfAborted();
    return runs.ok ? success(Object.freeze({ review: review.value, runs: runs.value })) : runs;
  }
}
