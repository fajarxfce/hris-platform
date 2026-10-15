import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type {
  PayrollAmendmentReviewDecision,
  PayrollAmendmentReviewSubmission,
  PayrollAmendmentReviewWithdrawal,
} from "../../domain/entities/payroll-amendment-review-command";
import type { PayrollAmendmentReviewRepository } from "../../domain/repositories/payroll-amendment-review-repository";
import type { PayrollAmendmentReviewDataSource } from "../datasources/payroll-amendment-review-data-source";
import { toPayrollAmendmentReceipt } from "../mappers/payroll-amendment-command-mapper";
import {
  toPayrollAmendmentReviewDetails,
  toPayrollAmendmentReviews,
} from "../mappers/payroll-amendment-review-mapper";

export class RemotePayrollAmendmentReviewRepository implements PayrollAmendmentReviewRepository {
  constructor(private readonly source: PayrollAmendmentReviewDataSource) {}
  list(company: CompanyId, amendment: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollAmendmentReviews(await this.source.list(company, amendment, signal), amendment),
    );
  }
  get(company: CompanyId, id: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toPayrollAmendmentReviewDetails(await this.source.get(company, id, signal), company, id),
    );
  }
  submit(
    company: CompanyId,
    amendment: string,
    operation: OperationId,
    input: PayrollAmendmentReviewSubmission,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toPayrollAmendmentReceipt(
        await this.source.submit(
          company,
          amendment,
          input.reviewId,
          operation,
          {
            expectedVersion: input.expectedVersion,
            calculationId: input.calculationId,
            reason: input.reason,
          },
          signal,
        ),
        amendment,
        input.expectedVersion,
      ),
    );
  }
  decide(
    company: CompanyId,
    id: string,
    operation: OperationId,
    input: PayrollAmendmentReviewDecision,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toPayrollAmendmentReceipt(
        await this.source.decide(
          company,
          input.amendmentId,
          id,
          operation,
          {
            expectedVersion: input.expectedVersion,
            expectedReviewVersion: input.expectedReviewVersion,
            expectedApprovalVersion: input.expectedApprovalVersion,
            decision: input.decision,
            reason: input.reason,
          },
          signal,
        ),
        input.amendmentId,
        input.expectedVersion,
      ),
    );
  }
  withdraw(
    company: CompanyId,
    id: string,
    operation: OperationId,
    input: PayrollAmendmentReviewWithdrawal,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toPayrollAmendmentReceipt(
        await this.source.withdraw(
          company,
          input.amendmentId,
          id,
          operation,
          {
            expectedVersion: input.expectedVersion,
            expectedReviewVersion: input.expectedReviewVersion,
            expectedApprovalVersion: input.expectedApprovalVersion,
            reason: input.reason,
          },
          signal,
        ),
        input.amendmentId,
        input.expectedVersion,
      ),
    );
  }
}
