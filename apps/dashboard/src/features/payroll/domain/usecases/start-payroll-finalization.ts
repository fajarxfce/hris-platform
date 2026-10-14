import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollFinalizationStart } from "../entities/payroll-finalization-command";
import { validPayrollCalculationVersion } from "../policies/payroll-calculation-policy";
import { payrollFinalizationReasonFailure } from "../policies/payroll-finalization-policy";
import type { PayrollFinalizationRepository } from "../repositories/payroll-finalization-repository";
export class StartPayrollFinalization {
  constructor(private readonly finalizations: PayrollFinalizationRepository) {}
  execute(
    access: CompanyAccess,
    run: string,
    operation: OperationId,
    input: PayrollFinalizationStart,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!access.permissions.includes("payroll.finalize"))
      return Promise.resolve(failed("access_denied"));
    if (
      !isUuid(run) ||
      !isUuid(operation) ||
      !isUuid(input.id) ||
      !isUuid(input.reviewId) ||
      !validPayrollCalculationVersion(input.expectedRunVersion, 23) ||
      !validPayrollCalculationVersion(input.expectedReviewVersion, 8) ||
      input.expectedReviewVersion < 1 ||
      !validPayrollCalculationVersion(input.expectedApprovalVersion) ||
      input.expectedApprovalVersion < 1
    )
      return Promise.resolve(failed("invalid_payroll_finalization"));
    const failure = payrollFinalizationReasonFailure(input.reason);
    if (failure) return Promise.resolve({ ok: false, failure });
    return this.finalizations.start(
      access.companyId,
      run.toLowerCase(),
      operation.toLowerCase() as OperationId,
      Object.freeze({
        ...input,
        id: input.id.toLowerCase(),
        reviewId: input.reviewId.toLowerCase(),
      }),
      signal,
    );
  }
}
