import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollCalculationRecoveryAction } from "../entities/payroll-calculation-command";
import type { PayrollRunDetails } from "../entities/payroll-run-details";
import type { PayrollCalculationRepository } from "../repositories/payroll-calculation-repository";
export class ReviewPayrollRunRecovery {
  constructor(private readonly calculations: PayrollCalculationRepository) {}
  async execute(
    access: CompanyAccess,
    id: string,
    action: PayrollCalculationRecoveryAction,
    signal: AbortSignal,
  ): Promise<Result<PayrollRunDetails>> {
    signal.throwIfAborted();
    if (!access.permissions.includes("payroll.calculate")) return failed("access_denied");
    if (!isUuid(id)) return failed("payroll_run_not_found");
    const result = await this.calculations.run(access.companyId, id.toLowerCase(), null, 1, signal);
    signal.throwIfAborted();
    if (!result.ok) return result;
    return result.value.availableActions.includes(action === "resume" ? "RESUME" : "ABANDON")
      ? result
      : failed("payroll_calculation_action_unavailable");
  }
}
