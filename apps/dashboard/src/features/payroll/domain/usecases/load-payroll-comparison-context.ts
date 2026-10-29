import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollComparisonContext } from "../entities/payroll-run-comparison";
import { canReadPayrollPeriods, validPayrollMonth } from "../policies/payroll-period-policy";
import type { PayrollCalculationRepository } from "../repositories/payroll-calculation-repository";
import type { PayrollPeriodRepository } from "../repositories/payroll-period-repository";
export class LoadPayrollComparisonContext {
  constructor(
    private readonly calculations: PayrollCalculationRepository,
    private readonly periods: PayrollPeriodRepository,
  ) {}
  async execute(
    access: CompanyAccess,
    id: string,
    month: string | null,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<PayrollComparisonContext>> {
    signal.throwIfAborted();
    if (!canReadPayrollPeriods(access.permissions)) return failed("access_denied");
    if (!isUuid(id)) return failed("payroll_run_not_found");
    if ((month !== null && !validPayrollMonth(month)) || (after !== null && !isUuid(after)))
      return failed("invalid_payroll_comparison");
    const loaded = await this.calculations.run(access.companyId, id.toLowerCase(), null, 1, signal);
    signal.throwIfAborted();
    if (!loaded.ok) return loaded;
    const run = loaded.value.run;
    if (
      !["CALCULATED", "FINALIZED"].includes(run.status) ||
      run.succeeded !== run.totalEmployees ||
      run.failed !== 0
    )
      return failed("payroll_comparison_unavailable");
    const previous = new Date(`${run.earningsMonth}-01T00:00:00Z`);
    previous.setUTCMonth(previous.getUTCMonth() - 1);
    const selected = month ?? previous.toISOString().slice(0, 7);
    if (selected >= run.earningsMonth) return failed("invalid_payroll_comparison");
    if (!validPayrollMonth(selected))
      return success(
        Object.freeze({
          run,
          month: "",
          periods: Object.freeze({ items: Object.freeze([]), nextCursor: null }),
        }),
      );
    const found = await this.periods.list(
      access.companyId,
      { from: selected, until: selected, status: null, after },
      signal,
    );
    signal.throwIfAborted();
    return found.ok
      ? success(Object.freeze({ run, month: selected, periods: found.value }))
      : found;
  }
}
