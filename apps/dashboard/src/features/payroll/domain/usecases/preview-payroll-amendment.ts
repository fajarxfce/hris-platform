import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentTerms } from "../entities/payroll-amendment";
import type { PayrollAmendmentPreparation } from "../entities/payroll-amendment-preparation";
import {
  canAuthorPayrollAmendments,
  normalizeAmendmentTerms,
  validateAmendmentProposal,
} from "../policies/payroll-amendment-authoring-policy";
import type { PayrollAmendmentSourceRepository } from "../repositories/payroll-amendment-source-repository";
export class PreviewPayrollAmendment {
  constructor(private readonly sources: PayrollAmendmentSourceRepository) {}
  execute(
    access: CompanyAccess,
    preparation: PayrollAmendmentPreparation,
    terms: PayrollAmendmentTerms,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (
      !canAuthorPayrollAmendments(access.permissions) ||
      preparation.companyId !== access.companyId
    )
      return Promise.resolve(failed("access_denied"));
    const normalized = normalizeAmendmentTerms(terms),
      failure = validateAmendmentProposal(preparation, normalized);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.sources.preview(
      access.companyId,
      preparation.firstAssessmentId,
      normalized,
      signal,
    );
  }
}
