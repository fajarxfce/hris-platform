import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  PayrollPublicationPage,
  PayrollPublicationSearch,
} from "../entities/payroll-publication-search";
import {
  canReadPayrollFinalizations,
  validPayrollPublicationSearch,
} from "../policies/payroll-finalization-policy";
import type { PayrollFinalizationRepository } from "../repositories/payroll-finalization-repository";
export class ListPayrollPublicationRuns {
  constructor(private readonly finalizations: PayrollFinalizationRepository) {}
  execute(
    access: CompanyAccess,
    search: PayrollPublicationSearch,
    signal: AbortSignal,
  ): Promise<Result<PayrollPublicationPage>> {
    signal.throwIfAborted();
    if (!canReadPayrollFinalizations(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!validPayrollPublicationSearch(search)) return Promise.resolve(failed("invalid_page"));
    return this.finalizations.runs(
      access.companyId,
      { ...search, after: search.after?.toLowerCase() ?? null },
      signal,
    );
  }
}
