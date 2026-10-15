import type { PayrollAmendmentReceiptDto } from "../models/payroll-amendment-command-dto";
import type {
  PayrollAmendmentReviewDecisionDto,
  PayrollAmendmentReviewDetailsDto,
  PayrollAmendmentReviewSubmissionDto,
  PayrollAmendmentReviewSummaryDto,
  PayrollAmendmentReviewWithdrawalDto,
} from "../models/payroll-amendment-review-dto";

export interface PayrollAmendmentReviewDataSource {
  list(
    company: string,
    amendment: string,
    signal: AbortSignal,
  ): Promise<readonly PayrollAmendmentReviewSummaryDto[]>;
  get(company: string, id: string, signal: AbortSignal): Promise<PayrollAmendmentReviewDetailsDto>;
  submit(
    company: string,
    amendment: string,
    id: string,
    operation: string,
    input: PayrollAmendmentReviewSubmissionDto,
    signal: AbortSignal,
  ): Promise<PayrollAmendmentReceiptDto>;
  decide(
    company: string,
    amendment: string,
    id: string,
    operation: string,
    input: PayrollAmendmentReviewDecisionDto,
    signal: AbortSignal,
  ): Promise<PayrollAmendmentReceiptDto>;
  withdraw(
    company: string,
    amendment: string,
    id: string,
    operation: string,
    input: PayrollAmendmentReviewWithdrawalDto,
    signal: AbortSignal,
  ): Promise<PayrollAmendmentReceiptDto>;
}
