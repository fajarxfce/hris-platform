import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canPreparePayrollPeriod } from "../policies/payroll-period-policy";
import type { PayrollPeriodRepository } from "../repositories/payroll-period-repository";
export class ReviewPayrollPeriodCancellation {
  constructor(private readonly periods: PayrollPeriodRepository) {}
  async execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canPreparePayrollPeriod(access.permissions)) return failed("access_denied");
    if (!isUuid(id)) return failed("payroll_period_not_found");
    const result = await this.periods.details(
      access.companyId,
      id.toLowerCase(),
      null,
      null,
      signal,
    );
    signal.throwIfAborted();
    if (!result.ok) return result;
    if (result.value.period.status !== "DRAFT") return failed("payroll_period_not_draft");
    if (result.value.period.version >= 255) return failed("payroll_period_revision_limit");
    return result;
  }
}
