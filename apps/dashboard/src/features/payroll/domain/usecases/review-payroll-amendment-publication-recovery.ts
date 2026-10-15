import { isUuid } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canPublishPayrollAmendments } from "../policies/payroll-amendment-publication-policy";
import type { PayrollAmendmentPublicationRepository } from "../repositories/payroll-amendment-publication-repository";

export class ReviewPayrollAmendmentPublicationRecovery {
  constructor(private readonly publications: PayrollAmendmentPublicationRepository) {}
  async execute(access: CompanyAccess, amendment: string, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canPublishPayrollAmendments(access.permissions)) return failed("access_denied");
    if (![amendment, id].every(isUuid)) return failed("payroll_amendment_publication_not_found");
    const result = await this.publications.list(access.companyId, amendment.toLowerCase(), signal);
    signal.throwIfAborted();
    if (!result.ok) return result;
    const found = result.value.find((row) => row.publication.id === id.toLowerCase());
    if (!found) return failed("payroll_amendment_publication_not_found");
    return found.availableActions.includes("RECOVER")
      ? success(found)
      : failed("payroll_amendment_recovery_unavailable");
  }
}
