import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { ManagedAccountReview } from "../../domain/entities/managed-account-review";
export type AccountAccessEditorState = Readonly<{
  stage: "loading" | "editing" | "saving" | "unconfirmed" | "conflict" | "saved" | "unavailable";
  review: ManagedAccountReview | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialAccountAccessEditorState: AccountAccessEditorState = Object.freeze({
  stage: "loading",
  review: null,
  failure: null,
  receipt: null,
  operationId: null,
});
