import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { ExpensePaymentBatchId } from "../entities/expense-payment-batch";
import type { ExpensePaymentReview } from "../entities/expense-payment-review";
import type { ExpensePaymentSearch } from "../entities/expense-payment-search";
import type { ExpensePaymentPage } from "../entities/expense-payment-summary";
export interface ExpensePaymentRepository {
  list(
    company: CompanyId,
    search: ExpensePaymentSearch,
    signal: AbortSignal,
  ): Promise<Result<ExpensePaymentPage>>;
  review(
    company: CompanyId,
    id: ExpensePaymentBatchId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<ExpensePaymentReview>>;
  worksheet(
    company: CompanyId,
    id: ExpensePaymentBatchId,
    signal: AbortSignal,
  ): Promise<Result<string>>;
}
