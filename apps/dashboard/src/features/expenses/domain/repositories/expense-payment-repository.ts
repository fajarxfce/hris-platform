import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { ExpensePayablePage } from "../entities/expense-payable";
import type { ExpensePayableSearch } from "../entities/expense-payable-search";
import type { ExpensePaymentBatchId } from "../entities/expense-payment-batch";
import type { ExpensePaymentPreparationCommand } from "../entities/expense-payment-preparation-command";
import type { ExpensePaymentReview } from "../entities/expense-payment-review";
import type { ExpensePaymentSearch } from "../entities/expense-payment-search";
import type { ExpensePaymentPage } from "../entities/expense-payment-summary";
import type { ExpensePaymentTransitionCommand } from "../entities/expense-payment-transition-command";
import type { ExpenseReconciliationCommand } from "../entities/expense-reconciliation-command";
export interface ExpensePaymentRepository {
  payables(
    company: CompanyId,
    search: ExpensePayableSearch,
    signal: AbortSignal,
  ): Promise<Result<ExpensePayablePage>>;
  prepare(
    company: CompanyId,
    operation: OperationId,
    command: ExpensePaymentPreparationCommand,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  reconcile(
    company: CompanyId,
    operation: OperationId,
    command: ExpenseReconciliationCommand,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  transition(
    company: CompanyId,
    operation: OperationId,
    command: ExpensePaymentTransitionCommand,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
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
