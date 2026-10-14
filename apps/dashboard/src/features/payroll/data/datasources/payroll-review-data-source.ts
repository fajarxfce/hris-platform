import type {
  PayrollReviewDecisionDto,
  PayrollReviewDetailsDto,
  PayrollReviewPreparationDto,
  PayrollReviewReceiptDto,
  PayrollReviewSubmissionDto,
  PayrollReviewsDto,
  PayrollReviewWithdrawalDto,
} from "../models/payroll-review-dto";
export interface PayrollReviewDataSource {
  prepare(company: string, run: string, signal: AbortSignal): Promise<PayrollReviewPreparationDto>;
  list(company: string, run: string, signal: AbortSignal): Promise<PayrollReviewsDto>;
  get(company: string, id: string, signal: AbortSignal): Promise<PayrollReviewDetailsDto>;
  submit(
    company: string,
    run: string,
    operation: string,
    input: PayrollReviewSubmissionDto,
    signal: AbortSignal,
  ): Promise<PayrollReviewReceiptDto>;
  decide(
    company: string,
    id: string,
    operation: string,
    input: PayrollReviewDecisionDto,
    signal: AbortSignal,
  ): Promise<PayrollReviewReceiptDto>;
  withdraw(
    company: string,
    id: string,
    operation: string,
    input: PayrollReviewWithdrawalDto,
    signal: AbortSignal,
  ): Promise<PayrollReviewReceiptDto>;
}
