import type { ExpensePaymentPageDto, ExpensePaymentReviewDto } from "../models/expense-payment-dto";
export interface ExpensePaymentDataSource {
  list(
    company: string,
    from: string,
    until: string,
    status: string | null,
    after: string | null,
    signal: AbortSignal,
  ): Promise<ExpensePaymentPageDto>;
  review(
    company: string,
    id: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<ExpensePaymentReviewDto>;
  worksheet(company: string, id: string, signal: AbortSignal): Promise<string>;
}
