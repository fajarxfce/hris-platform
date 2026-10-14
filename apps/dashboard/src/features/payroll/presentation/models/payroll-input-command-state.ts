import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { PayrollInputReview } from "../../domain/entities/payroll-input-review";

export type PayrollInputCommandState = Readonly<{
  stage: "loading" | "editing" | "saving" | "conflict" | "unconfirmed" | "saved" | "unavailable";
  context: PayrollInputReview | null;
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
}>;
export const initialPayrollInputCommandState: PayrollInputCommandState = {
  stage: "loading",
  context: null,
  failure: null,
  operationId: null,
  receipt: null,
};
