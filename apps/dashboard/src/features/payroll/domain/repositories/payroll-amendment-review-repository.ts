import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { PayrollAmendmentReviewSummary } from "../entities/payroll-amendment";
import type {
  PayrollAmendmentReviewDecision,
  PayrollAmendmentReviewSubmission,
  PayrollAmendmentReviewWithdrawal,
} from "../entities/payroll-amendment-review-command";
import type { PayrollAmendmentReviewDetails } from "../entities/payroll-amendment-review-details";

export interface PayrollAmendmentReviewRepository {
  list(
    company: CompanyId,
    amendment: string,
    signal: AbortSignal,
  ): Promise<Result<readonly PayrollAmendmentReviewSummary[]>>;
  get(
    company: CompanyId,
    id: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollAmendmentReviewDetails>>;
  submit(
    company: CompanyId,
    amendment: string,
    operation: OperationId,
    input: PayrollAmendmentReviewSubmission,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  decide(
    company: CompanyId,
    id: string,
    operation: OperationId,
    input: PayrollAmendmentReviewDecision,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  withdraw(
    company: CompanyId,
    id: string,
    operation: OperationId,
    input: PayrollAmendmentReviewWithdrawal,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
