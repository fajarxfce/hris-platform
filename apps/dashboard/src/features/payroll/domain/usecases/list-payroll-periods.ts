import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPeriodSearch } from "../entities/payroll-period-search";
import { canReadPayrollPeriods, validPayrollPeriodSearch } from "../policies/payroll-period-policy";
import type { PayrollPeriodRepository } from "../repositories/payroll-period-repository";
export class ListPayrollPeriods {
  constructor(private readonly periods: PayrollPeriodRepository) {}
  execute(access: CompanyAccess, search: PayrollPeriodSearch, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canReadPayrollPeriods(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!validPayrollPeriodSearch(search)) return Promise.resolve(failed("invalid_page"));
    return this.periods.list(access.companyId, search, signal);
  }
}
