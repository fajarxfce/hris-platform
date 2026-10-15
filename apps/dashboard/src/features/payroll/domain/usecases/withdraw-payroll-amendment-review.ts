import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentReviewWithdrawal } from "../entities/payroll-amendment-review-command";
import {
  canActOnAmendmentReview,
  validateAmendmentReviewTransition,
} from "../policies/payroll-amendment-review-policy";
import type { PayrollAmendmentReviewRepository } from "../repositories/payroll-amendment-review-repository";

export class WithdrawPayrollAmendmentReview {
  constructor(private readonly reviews: PayrollAmendmentReviewRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    operation: OperationId,
    input: PayrollAmendmentReviewWithdrawal,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canActOnAmendmentReview(access.permissions, "WITHDRAW"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id) || !isUuid(operation))
      return Promise.resolve(failed("invalid_payroll_amendment"));
    const normalized = Object.freeze({
        amendmentId: input.amendmentId.toLowerCase(),
        expectedVersion: input.expectedVersion,
        expectedReviewVersion: input.expectedReviewVersion,
        expectedApprovalVersion: input.expectedApprovalVersion,
        reason: input.reason.trim(),
      }),
      failure = validateAmendmentReviewTransition(normalized);
    if (failure) return Promise.resolve({ ok: false as const, failure });
    return this.reviews.withdraw(access.companyId, id.toLowerCase(), operation, normalized, signal);
  }
}
