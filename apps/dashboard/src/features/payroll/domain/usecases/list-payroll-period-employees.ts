import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadPayrollPeriods } from "../policies/payroll-period-policy";
import type { PayrollPeriodRepository } from "../repositories/payroll-period-repository";
export class ListPayrollPeriodEmployees {
  constructor(private readonly periods: PayrollPeriodRepository) {}
  execute(access: CompanyAccess, query: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadPayrollPeriods(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (query.length > 120 || (after !== null && (after.trim().length === 0 || after.length > 32)))
      return Promise.resolve(failed("invalid_page"));
    return this.periods.employees(access.companyId, query.trim(), after, signal);
  }
}
