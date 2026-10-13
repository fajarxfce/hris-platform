import type { ExpensePolicyChangeDto } from "../models/expense-policy-change-dto";
import type {
  ExpenseCategoryPageDto,
  ExpensePolicyReviewDto,
  ExpenseReceiptDto,
} from "../models/expense-policy-dto";

export interface ExpensePolicyDataSource {
  save(
    company: string,
    id: string,
    operation: string,
    change: ExpensePolicyChangeDto,
    signal: AbortSignal,
  ): Promise<ExpenseReceiptDto>;
  list(
    company: string,
    active: boolean | null,
    after: string | null,
    signal: AbortSignal,
  ): Promise<ExpenseCategoryPageDto>;
  get(
    company: string,
    id: string,
    historyAfter: string | null,
    signal: AbortSignal,
  ): Promise<ExpensePolicyReviewDto>;
}
