import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { PayrollPaymentReview } from "../../domain/entities/payroll-payment-review";
export type PayrollPaymentTransitionState = Readonly<{
  stage:
    | "loading"
    | "reviewing"
    | "submitting"
    | "unconfirmed"
    | "conflict"
    | "unavailable"
    | "saved";
  review: PayrollPaymentReview | null;
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
  selectedItemId: string | null;
}>;
export const initialPayrollPaymentTransitionState: PayrollPaymentTransitionState = {
  stage: "loading",
  review: null,
  failure: null,
  operationId: null,
  receipt: null,
  selectedItemId: null,
};
