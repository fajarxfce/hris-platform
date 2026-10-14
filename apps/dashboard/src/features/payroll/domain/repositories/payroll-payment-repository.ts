import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { PayrollPayablePage } from "../entities/payroll-payable";
import type { PayrollPayableSearch } from "../entities/payroll-payable-search";
import type { PayrollPaymentBatchId } from "../entities/payroll-payment-batch";
import type { PayrollPaymentPreparationCommand } from "../entities/payroll-payment-preparation-command";
import type { PayrollPaymentReconciliationCommand } from "../entities/payroll-payment-reconciliation-command";
import type { PayrollPaymentReview } from "../entities/payroll-payment-review";
import type { PayrollPaymentSearch } from "../entities/payroll-payment-search";
import type { PayrollPaymentPage } from "../entities/payroll-payment-summary";
import type { PayrollPaymentTransitionCommand } from "../entities/payroll-payment-transition-command";
export interface PayrollPaymentRepository {
  payables(
    company: CompanyId,
    search: PayrollPayableSearch,
    signal: AbortSignal,
  ): Promise<Result<PayrollPayablePage>>;
  prepare(
    company: CompanyId,
    operation: OperationId,
    command: PayrollPaymentPreparationCommand,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  reconcile(
    company: CompanyId,
    operation: OperationId,
    command: PayrollPaymentReconciliationCommand,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  transition(
    company: CompanyId,
    operation: OperationId,
    command: PayrollPaymentTransitionCommand,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
  list(
    company: CompanyId,
    search: PayrollPaymentSearch,
    signal: AbortSignal,
  ): Promise<Result<PayrollPaymentPage>>;
  review(
    company: CompanyId,
    id: PayrollPaymentBatchId,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<PayrollPaymentReview>>;
  worksheet(
    company: CompanyId,
    id: PayrollPaymentBatchId,
    signal: AbortSignal,
  ): Promise<Result<string>>;
}
