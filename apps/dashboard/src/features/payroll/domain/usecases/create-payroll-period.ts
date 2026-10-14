import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPeriodCreation } from "../entities/payroll-period-creation";
import {
  canPreparePayrollPeriod,
  normalizePayrollPeriodCreation,
  validatePayrollPeriodCreation,
} from "../policies/payroll-period-policy";
import type { PayrollPeriodRepository } from "../repositories/payroll-period-repository";
export class CreatePayrollPeriod {
  constructor(private readonly periods: PayrollPeriodRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: PayrollPeriodCreation,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canPreparePayrollPeriod(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_payroll_period"));
    const failure = validatePayrollPeriodCreation(input);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.periods.create(
      access.companyId,
      operation,
      normalizePayrollPeriodCreation(input),
      signal,
    );
  }
}
