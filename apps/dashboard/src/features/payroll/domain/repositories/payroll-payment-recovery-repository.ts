import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { PayrollPaymentRecoveryCommand } from "../entities/payroll-payment-recovery-command";
import type { PayrollPaymentRecoveryReview } from "../entities/payroll-payment-recovery-review";

export interface PayrollPaymentRecoveryRepository {
  review(
    company: CompanyId,
    assessmentId: string,
    signal: AbortSignal,
  ): Promise<Result<PayrollPaymentRecoveryReview>>;
  confirm(
    company: CompanyId,
    operation: OperationId,
    command: PayrollPaymentRecoveryCommand,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
