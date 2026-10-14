import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { PayrollPaymentPreparation } from "../../domain/entities/payroll-payment-preparation";
import type { PayrollPaymentSelection } from "../../domain/entities/payroll-payment-selection";
export type PayrollPaymentPreparationPanel = Readonly<
  | { kind: "closed" }
  | { kind: "payables" }
  | { kind: "destination"; assessmentId: string; dirty: boolean }
>;
export type PayrollPaymentPreparationState = Readonly<{
  stage:
    | "loading"
    | "editing"
    | "reviewing"
    | "submitting"
    | "unconfirmed"
    | "unavailable"
    | "saved";
  items: readonly PayrollPaymentSelection[];
  review: PayrollPaymentPreparation | null;
  panel: PayrollPaymentPreparationPanel;
  failure: Failure | null;
  entryFailure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
}>;
export const initialPayrollPaymentPreparationState: PayrollPaymentPreparationState = {
  stage: "loading",
  items: [],
  review: null,
  panel: { kind: "closed" },
  failure: null,
  entryFailure: null,
  operationId: null,
  receipt: null,
};
