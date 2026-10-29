import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canReadPayrollPeriods } from "../policies/payroll-period-policy";
import type { PayrollComparisonRepository } from "../repositories/payroll-comparison-repository";
export class LoadPayrollRunComparison {
  constructor(private readonly source: PayrollComparisonRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    baseline: string,
    after: string | null,
    changedOnly: boolean,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canReadPayrollPeriods(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(id) || !isUuid(baseline) || id.toLowerCase() === baseline.toLowerCase())
      return Promise.resolve(failed("invalid_payroll_comparison"));
    if (after !== null && !isUuid(after)) return Promise.resolve(failed("invalid_page"));
    return this.source.compare(
      access.companyId,
      id.toLowerCase(),
      baseline.toLowerCase(),
      after?.toLowerCase() ?? null,
      changedOnly,
      signal,
    );
  }
}
