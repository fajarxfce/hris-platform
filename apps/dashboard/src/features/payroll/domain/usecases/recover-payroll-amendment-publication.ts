import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentPublicationRecovery } from "../entities/payroll-amendment-publication-command";
import { validatePayrollAmendmentCommand } from "../policies/payroll-amendment-command-policy";
import { canPublishPayrollAmendments } from "../policies/payroll-amendment-publication-policy";
import type { PayrollAmendmentPublicationRepository } from "../repositories/payroll-amendment-publication-repository";

export class RecoverPayrollAmendmentPublication {
  constructor(private readonly publications: PayrollAmendmentPublicationRepository) {}
  execute(
    access: CompanyAccess,
    amendment: string,
    id: string,
    operation: OperationId,
    input: PayrollAmendmentPublicationRecovery,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canPublishPayrollAmendments(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (![amendment, id, operation].every(isUuid))
      return Promise.resolve(failed("invalid_payroll_amendment_publication"));
    if (!Number.isSafeInteger(input.expectedJobVersion) || input.expectedJobVersion < 0)
      return Promise.resolve(failed("invalid_job_version"));
    const normalized = Object.freeze({
        expectedVersion: input.expectedVersion,
        expectedJobVersion: input.expectedJobVersion,
        reason: input.reason.trim(),
      }),
      failure = validatePayrollAmendmentCommand(normalized);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.publications.recover(
      access.companyId,
      amendment.toLowerCase(),
      id.toLowerCase(),
      operation,
      normalized,
      signal,
    );
  }
}
