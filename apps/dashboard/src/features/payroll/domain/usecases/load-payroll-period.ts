import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadPayrollPeriods } from "../policies/payroll-period-policy";
import type { PayrollPeriodRepository } from "../repositories/payroll-period-repository";
export class LoadPayrollPeriod {
  constructor(private readonly periods: PayrollPeriodRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    memberAfter: string | null,
    historyAfter: string | null,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadPayrollPeriods(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("payroll_period_not_found"));
    if (
      (memberAfter !== null && !isUuid(memberAfter)) ||
      (historyAfter !== null &&
        (!/^(?:0|[1-9]\d{0,2})$/u.test(historyAfter) || Number(historyAfter) > 255))
    )
      return Promise.resolve(failed("invalid_page"));
    return this.periods.details(
      access.companyId,
      id.toLowerCase(),
      memberAfter?.toLowerCase() ?? null,
      historyAfter,
      signal,
    );
  }
}
