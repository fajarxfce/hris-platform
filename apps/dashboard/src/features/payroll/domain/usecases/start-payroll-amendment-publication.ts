import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentPublicationStart } from "../entities/payroll-amendment-publication-command";
import { validatePayrollAmendmentCommand } from "../policies/payroll-amendment-command-policy";
import { canPublishPayrollAmendments } from "../policies/payroll-amendment-publication-policy";
import type { PayrollAmendmentPublicationRepository } from "../repositories/payroll-amendment-publication-repository";

export class StartPayrollAmendmentPublication {
  constructor(private readonly publications: PayrollAmendmentPublicationRepository) {}
  execute(
    access: CompanyAccess,
    amendment: string,
    operation: OperationId,
    input: PayrollAmendmentPublicationStart,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canPublishPayrollAmendments(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (
      ![amendment, operation, input.publicationId, input.reviewId, input.calculationId].every(
        isUuid,
      ) ||
      !Number.isInteger(input.expectedReviewVersion) ||
      input.expectedReviewVersion < 1 ||
      input.expectedReviewVersion > 8 ||
      !Number.isSafeInteger(input.expectedApprovalVersion) ||
      input.expectedApprovalVersion < 1
    )
      return Promise.resolve(failed("invalid_payroll_amendment_publication"));
    const normalized = Object.freeze({
        publicationId: input.publicationId.toLowerCase(),
        expectedVersion: input.expectedVersion,
        reviewId: input.reviewId.toLowerCase(),
        expectedReviewVersion: input.expectedReviewVersion,
        expectedApprovalVersion: input.expectedApprovalVersion,
        calculationId: input.calculationId.toLowerCase(),
        reason: input.reason.trim(),
      }),
      failure = validatePayrollAmendmentCommand(normalized);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    if (normalized.expectedVersion > 996)
      return Promise.resolve(failed("payroll_amendment_revision_limit"));
    return this.publications.start(
      access.companyId,
      amendment.toLowerCase(),
      operation,
      normalized,
      signal,
    );
  }
}
