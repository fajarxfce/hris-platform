import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentReviewSubmission } from "../entities/payroll-amendment-review-command";
import { canAuthorPayrollAmendments } from "../policies/payroll-amendment-authoring-policy";
import { validatePayrollAmendmentCommand } from "../policies/payroll-amendment-command-policy";
import type { PayrollAmendmentReviewRepository } from "../repositories/payroll-amendment-review-repository";

export class SubmitPayrollAmendmentReview {
  constructor(private readonly reviews: PayrollAmendmentReviewRepository) {}
  execute(
    access: CompanyAccess,
    amendment: string,
    operation: OperationId,
    input: PayrollAmendmentReviewSubmission,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canAuthorPayrollAmendments(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (![amendment, operation, input.reviewId, input.calculationId].every(isUuid))
      return Promise.resolve(failed("invalid_payroll_amendment"));
    const normalized = Object.freeze({
        reviewId: input.reviewId.toLowerCase(),
        calculationId: input.calculationId.toLowerCase(),
        expectedVersion: input.expectedVersion,
        reason: input.reason.trim(),
      }),
      failure = validatePayrollAmendmentCommand(normalized);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.reviews.submit(
      access.companyId,
      amendment.toLowerCase(),
      operation,
      normalized,
      signal,
    );
  }
}
