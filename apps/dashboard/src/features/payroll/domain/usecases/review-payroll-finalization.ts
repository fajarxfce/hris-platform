import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollFinalizationPreparation } from "../entities/payroll-finalization-preparation";
import type { PayrollFinalizationRepository } from "../repositories/payroll-finalization-repository";
export class ReviewPayrollFinalization {
  constructor(private readonly finalizations: PayrollFinalizationRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollFinalizationPreparation>> {
    signal.throwIfAborted();
    if (!access.permissions.includes("payroll.finalize"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("payroll_run_not_found"));
    return this.finalizations.prepare(access.companyId, id.toLowerCase(), signal);
  }
}
