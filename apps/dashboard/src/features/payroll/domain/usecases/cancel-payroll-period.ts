import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canPreparePayrollPeriod } from "../policies/payroll-period-policy";
import type { PayrollPeriodRepository } from "../repositories/payroll-period-repository";
export class CancelPayrollPeriod {
  constructor(private readonly periods: PayrollPeriodRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    operation: OperationId,
    version: number,
    reason: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canPreparePayrollPeriod(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (
      !isUuid(id) ||
      !isUuid(operation) ||
      !Number.isSafeInteger(version) ||
      version < 0 ||
      version >= 255 ||
      reason.trim().length === 0 ||
      reason.length > 1000
    )
      return Promise.resolve(failed("invalid_payroll_period"));
    return this.periods.cancel(
      access.companyId,
      id.toLowerCase(),
      operation,
      version,
      reason.trim(),
      signal,
    );
  }
}
