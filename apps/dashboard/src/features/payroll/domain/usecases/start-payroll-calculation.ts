import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollCalculationStart } from "../entities/payroll-calculation-command";
import {
  validatePayrollCalculationFields,
  validPayrollCalculationVersion,
} from "../policies/payroll-calculation-policy";
import type { PayrollCalculationRepository } from "../repositories/payroll-calculation-repository";
export class StartPayrollCalculation {
  constructor(private readonly calculations: PayrollCalculationRepository) {}
  execute(
    access: CompanyAccess,
    period: string,
    operation: OperationId,
    input: PayrollCalculationStart,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!access.permissions.includes("payroll.calculate"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(period) || !isUuid(operation) || !isUuid(input.id))
      return Promise.resolve(failed("invalid_payroll_run"));
    const failure = validatePayrollCalculationFields(input);
    if (failure) return Promise.resolve({ ok: false, failure });
    if (
      !validPayrollCalculationVersion(input.expectedPeriodVersion, 254) ||
      !validPayrollCalculationVersion(input.expectedWorkPeriodVersion) ||
      !validPayrollCalculationVersion(input.expectedPolicyVersion, 999)
    )
      return Promise.resolve(failed("invalid_version"));
    return this.calculations.start(
      access.companyId,
      period.toLowerCase(),
      operation,
      Object.freeze({
        ...input,
        id: input.id.toLowerCase(),
        reviewReference: input.reviewReference.trim(),
        reason: input.reason.trim(),
      }),
      signal,
    );
  }
}
