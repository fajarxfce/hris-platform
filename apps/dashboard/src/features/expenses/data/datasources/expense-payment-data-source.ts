import type { ExpensePaymentPageDto, ExpensePaymentReviewDto } from "../models/expense-payment-dto";
import type {
  ExpensePaymentTransitionDto,
  ExpensePaymentTransitionReceiptDto,
} from "../models/expense-payment-transition-dto";
import type { ExpenseReconciliationDto } from "../models/expense-reconciliation-dto";
export interface ExpensePaymentDataSource {
  reconcile(
    company: string,
    id: string,
    operation: string,
    command: ExpenseReconciliationDto,
    signal: AbortSignal,
  ): Promise<ExpensePaymentTransitionReceiptDto>;
  transition(
    company: string,
    id: string,
    operation: string,
    action: "release" | "cancel",
    command: ExpensePaymentTransitionDto,
    signal: AbortSignal,
  ): Promise<ExpensePaymentTransitionReceiptDto>;
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
