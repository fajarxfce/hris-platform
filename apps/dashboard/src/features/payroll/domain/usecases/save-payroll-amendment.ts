import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentSave } from "../entities/payroll-amendment-preparation";
import {
  canAuthorPayrollAmendments,
  normalizeAmendmentTerms,
  validateAmendmentSave,
} from "../policies/payroll-amendment-authoring-policy";
import type { PayrollAmendmentRepository } from "../repositories/payroll-amendment-repository";
export class SavePayrollAmendment {
  constructor(private readonly amendments: PayrollAmendmentRepository) {}
  execute(
    access: CompanyAccess,
    amendment: string,
    operation: OperationId,
    input: PayrollAmendmentSave,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canAuthorPayrollAmendments(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(amendment) || !isUuid(operation))
      return Promise.resolve(failed("invalid_payroll_amendment"));
    const normalized: PayrollAmendmentSave = Object.freeze({
      ...normalizeAmendmentTerms(input),
      firstAssessmentId: input.firstAssessmentId.toLowerCase(),
      expectedVersion: input.expectedVersion,
      reason: input.reason.trim(),
    });
    const failure = validateAmendmentSave(normalized);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    // Current source acquisition belongs to preparation, not original-receipt recovery.
    return this.amendments.save(
      access.companyId,
      amendment.toLowerCase(),
      operation,
      normalized,
      signal,
    );
  }
}
