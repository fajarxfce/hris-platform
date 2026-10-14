import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { PayrollReview } from "../entities/payroll-review";
import type {
  PayrollReviewDecision,
  PayrollReviewSubmission,
  PayrollReviewWithdrawal,
} from "../entities/payroll-review-command";
import type { PayrollReviewDetails } from "../entities/payroll-review-details";
import type { PayrollReviewPreparation } from "../entities/payroll-review-preparation";
export interface PayrollReviewRepository {
  prepare(
    company: CompanyId,
    run: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollReviewPreparation>>;
  list(
    company: CompanyId,
    run: string,
    signal: AbortSignal,
  ): Promise<Result<readonly PayrollReview[]>>;
  get(company: CompanyId, id: string, signal: AbortSignal): Promise<Result<PayrollReviewDetails>>;
  submit(
    company: CompanyId,
    run: string,
    operation: OperationId,
    input: PayrollReviewSubmission,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  decide(
    company: CompanyId,
    id: string,
    operation: OperationId,
    input: PayrollReviewDecision,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  withdraw(
    company: CompanyId,
    id: string,
    operation: OperationId,
    input: PayrollReviewWithdrawal,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
