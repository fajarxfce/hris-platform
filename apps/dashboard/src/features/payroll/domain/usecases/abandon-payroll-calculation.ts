import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollCalculationRecovery } from "../entities/payroll-calculation-command";
import { validPayrollCalculationRecovery } from "../policies/payroll-calculation-policy";
import type { PayrollCalculationRepository } from "../repositories/payroll-calculation-repository";
export class AbandonPayrollCalculation {
  constructor(private readonly calculations: PayrollCalculationRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    operation: OperationId,
    input: PayrollCalculationRecovery,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!access.permissions.includes("payroll.calculate"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id) || !isUuid(operation) || !validPayrollCalculationRecovery(input))
      return Promise.resolve(failed("invalid_payroll_run_action"));
    return this.calculations.abandon(
      access.companyId,
      id.toLowerCase(),
      operation,
      Object.freeze({ ...input, reason: input.reason.trim() }),
      signal,
    );
  }
}
