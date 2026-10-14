import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollFinalizations } from "../entities/payroll-finalization-preparation";
import { canReadPayrollFinalizations } from "../policies/payroll-finalization-policy";
import type { PayrollFinalizationRepository } from "../repositories/payroll-finalization-repository";
export class LoadPayrollFinalizations {
  constructor(private readonly finalizations: PayrollFinalizationRepository) {}
  async execute(
    access: CompanyAccess,
    run: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollFinalizations>> {
    signal.throwIfAborted();
    if (!canReadPayrollFinalizations(access.permissions)) return failed("access_denied");
    if (!isUuid(run)) return failed("payroll_run_not_found");
    const preparation = await this.finalizations.prepare(
      access.companyId,
      run.toLowerCase(),
      signal,
    );
    signal.throwIfAborted();
    if (!preparation.ok) return preparation;
    const history = await this.finalizations.list(access.companyId, run.toLowerCase(), signal);
    signal.throwIfAborted();
    return history.ok
      ? success(Object.freeze({ preparation: preparation.value, finalizations: history.value }))
      : history;
  }
}
