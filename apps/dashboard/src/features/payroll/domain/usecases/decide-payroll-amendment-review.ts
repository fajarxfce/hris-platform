import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentReviewDecision } from "../entities/payroll-amendment-review-command";
import {
  canActOnAmendmentReview,
  validateAmendmentReviewTransition,
} from "../policies/payroll-amendment-review-policy";
import type { PayrollAmendmentReviewRepository } from "../repositories/payroll-amendment-review-repository";

export class DecidePayrollAmendmentReview {
  constructor(private readonly reviews: PayrollAmendmentReviewRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    operation: OperationId,
    input: PayrollAmendmentReviewDecision,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canActOnAmendmentReview(access.permissions, "APPROVE"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id) || !isUuid(operation) || !["APPROVE", "REJECT"].includes(input.decision))
      return Promise.resolve(failed("invalid_payroll_amendment"));
    const normalized = Object.freeze({
        amendmentId: input.amendmentId.toLowerCase(),
        expectedVersion: input.expectedVersion,
        expectedReviewVersion: input.expectedReviewVersion,
        expectedApprovalVersion: input.expectedApprovalVersion,
        decision: input.decision,
        reason: input.reason.trim(),
      }),
      failure = validateAmendmentReviewTransition(normalized);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.reviews.decide(access.companyId, id.toLowerCase(), operation, normalized, signal);
  }
}
