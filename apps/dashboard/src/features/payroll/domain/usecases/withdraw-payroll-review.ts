import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollReviewWithdrawal } from "../entities/payroll-review-command";
import { validPayrollCalculationVersion } from "../policies/payroll-calculation-policy";
import { payrollReviewReasonFailure } from "../policies/payroll-review-policy";
import type { PayrollReviewRepository } from "../repositories/payroll-review-repository";
export class WithdrawPayrollReview {
  constructor(private readonly reviews: PayrollReviewRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    operation: OperationId,
    input: PayrollReviewWithdrawal,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!access.permissions.includes("payroll.calculate"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id) || !isUuid(operation)) return Promise.resolve(failed("invalid_payroll_review"));

    if (
      !validPayrollCalculationVersion(input.expectedVersion, 9) ||
      !validPayrollCalculationVersion(input.expectedApprovalVersion)
    )
      return Promise.resolve(failed("invalid_version"));

    const failure = payrollReviewReasonFailure(input.reason, true);
    if (failure) return Promise.resolve({ ok: false, failure });
    return this.reviews.withdraw(
      access.companyId,
      id.toLowerCase(),
      operation,
      Object.freeze({ ...input, reason: input.reason.trim() }),
      signal,
    );
  }
}
