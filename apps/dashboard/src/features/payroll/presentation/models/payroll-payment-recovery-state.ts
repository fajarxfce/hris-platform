import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { PayrollPaymentRecoveryReview } from "../../domain/entities/payroll-payment-recovery-review";

export type PayrollPaymentRecoveryState = Readonly<{
  stage: "loading" | "ready" | "submitting" | "unconfirmed" | "conflict" | "unavailable" | "saved";
  review: PayrollPaymentRecoveryReview | null;
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
}>;

export const initialPayrollPaymentRecoveryState: PayrollPaymentRecoveryState = Object.freeze({
  stage: "loading",
  review: null,
  failure: null,
  operationId: null,
  receipt: null,
});
