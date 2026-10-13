import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { ExpensePaymentPreparation } from "../../domain/entities/expense-payment-preparation";
import type { ExpensePaymentSelection } from "../../domain/entities/expense-payment-selection";
export type ExpensePreparationPanel = Readonly<
  | { kind: "closed" }
  | { kind: "payables" }
  | { kind: "destination"; claimId: string; dirty: boolean }
>;
export type ExpensePreparationState = Readonly<{
  stage:
    | "loading"
    | "editing"
    | "reviewing"
    | "submitting"
    | "unconfirmed"
    | "unavailable"
    | "saved";
  items: readonly ExpensePaymentSelection[];
  review: ExpensePaymentPreparation | null;
  panel: ExpensePreparationPanel;
  failure: Failure | null;
  entryFailure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
}>;
export const initialExpensePreparationState: ExpensePreparationState = {
  stage: "loading",
  items: [],
  review: null,
  panel: { kind: "closed" },
  failure: null,
  entryFailure: null,
  operationId: null,
  receipt: null,
};
