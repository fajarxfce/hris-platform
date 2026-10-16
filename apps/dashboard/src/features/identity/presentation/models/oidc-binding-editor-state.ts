import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { OidcBindingReview } from "../../domain/entities/oidc-binding-review";

export type OidcBindingEditorState = Readonly<{
  stage: "loading" | "editing" | "saving" | "saved" | "conflict" | "unconfirmed" | "unavailable";
  review: OidcBindingReview | null;
  failure: Failure | null;
  operationId: string | null;
  receipt: MutationReceipt | null;
}>;
export const initialOidcBindingEditorState: OidcBindingEditorState = Object.freeze({
  stage: "loading",
  review: null,
  failure: null,
  operationId: null,
  receipt: null,
});
