import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { PayrollPaymentOutcome } from "../../domain/entities/payroll-payment-outcome";
import type { PayrollPaymentReview } from "../../domain/entities/payroll-payment-review";
export type PayrollPaymentReconciliationState = Readonly<{
  stage:
    | "loading"
    | "reviewing"
    | "submitting"
    | "unconfirmed"
    | "conflict"
    | "unavailable"
    | "saved";
  review: PayrollPaymentReview | null;
  results: readonly PayrollPaymentOutcome[];
  failure: Failure | null;
  entryFailure: Failure | null;
  entryDirty: boolean;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
  selectedItemId: string | null;
}>;
export const initialPayrollPaymentReconciliationState: PayrollPaymentReconciliationState = {
  stage: "loading",
  review: null,
  results: [],
  failure: null,
  entryFailure: null,
  entryDirty: false,
  operationId: null,
  receipt: null,
  selectedItemId: null,
};
