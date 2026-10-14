import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadPayrollPeriods } from "../policies/payroll-period-policy";
import type { PayrollCalculationRepository } from "../repositories/payroll-calculation-repository";
export class LoadPayrollRunEmployee {
  constructor(private readonly calculations: PayrollCalculationRepository) {}
  execute(access: CompanyAccess, id: string, employee: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadPayrollPeriods(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(id) || !isUuid(employee))
      return Promise.resolve(failed("payroll_run_result_not_found"));
    return this.calculations.employee(
      access.companyId,
      id.toLowerCase(),
      employee.toLowerCase(),
      signal,
    );
  }
}
