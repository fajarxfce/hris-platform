import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";

export type LifecycleCaseActionState = Readonly<{
  stage: "editing" | "saving" | "unconfirmed" | "conflict" | "saved";
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
}>;
export const initialLifecycleCaseActionState: LifecycleCaseActionState = Object.freeze({
  stage: "editing",
  failure: null,
  operationId: null,
  receipt: null,
});
