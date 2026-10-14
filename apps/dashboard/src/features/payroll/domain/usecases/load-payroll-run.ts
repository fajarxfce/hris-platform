import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadPayrollPeriods } from "../policies/payroll-period-policy";
import type { PayrollCalculationRepository } from "../repositories/payroll-calculation-repository";
export class LoadPayrollRun {
  constructor(private readonly calculations: PayrollCalculationRepository) {}
  execute(access: CompanyAccess, id: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadPayrollPeriods(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("payroll_run_not_found"));
    if (after !== null && (!/^[1-9]\d{0,3}$/u.test(after) || Number(after) > 5000))
      return Promise.resolve(failed("invalid_page"));
    return this.calculations.run(access.companyId, id.toLowerCase(), after, 50, signal);
  }
}
