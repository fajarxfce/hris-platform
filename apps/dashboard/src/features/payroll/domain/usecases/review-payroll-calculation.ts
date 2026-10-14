import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollCalculationRepository } from "../repositories/payroll-calculation-repository";
export class ReviewPayrollCalculation {
  constructor(private readonly calculations: PayrollCalculationRepository) {}
  execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!access.permissions.includes("payroll.calculate"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("payroll_period_not_found"));
    return this.calculations.review(access.companyId, id.toLowerCase(), signal);
  }
}
