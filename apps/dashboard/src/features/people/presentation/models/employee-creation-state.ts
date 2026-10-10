import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";

export type EmployeeCreationState = Readonly<{
  stage: "editing" | "saving" | "unconfirmed" | "saved" | "unavailable";
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: OperationId | null;
  startDate: string | null;
}>;
export const initialEmployeeCreationState: EmployeeCreationState = Object.freeze({
  stage: "editing",
  failure: null,
  receipt: null,
  operationId: null,
  startDate: null,
});
