import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { Employee } from "../../../people/domain/entities/employee";

export type LifecycleCaseCreationState = Readonly<{
  stage: "loading" | "unavailable" | "editing" | "saving" | "unconfirmed" | "saved";
  employee: Employee | null;
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
}>;
export const initialLifecycleCaseCreationState: LifecycleCaseCreationState = Object.freeze({
  stage: "loading",
  employee: null,
  failure: null,
  operationId: null,
  receipt: null,
});
