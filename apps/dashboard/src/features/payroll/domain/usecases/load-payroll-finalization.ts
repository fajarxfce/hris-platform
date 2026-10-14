import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollFinalizationDetails } from "../entities/payroll-finalization-details";
import { canReadPayrollFinalizations } from "../policies/payroll-finalization-policy";
import type { PayrollFinalizationRepository } from "../repositories/payroll-finalization-repository";
export class LoadPayrollFinalization {
  constructor(private readonly finalizations: PayrollFinalizationRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollFinalizationDetails>> {
    signal.throwIfAborted();
    if (!canReadPayrollFinalizations(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("payroll_finalization_not_found"));
    return this.finalizations.get(access.companyId, id.toLowerCase(), signal);
  }
}
