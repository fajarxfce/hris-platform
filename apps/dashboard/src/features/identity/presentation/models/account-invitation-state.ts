import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { ManagedAccountReview } from "../../domain/entities/managed-account-review";
export type AccountInvitationState = Readonly<{
  stage: "loading" | "editing" | "saving" | "unconfirmed" | "conflict" | "saved" | "unavailable";
  review: ManagedAccountReview | null;
  receipt: MutationReceipt | null;
  failure: Failure | null;
  operationId: string | null;
}>;
export const initialAccountInvitationState: AccountInvitationState = Object.freeze({
  stage: "loading",
  review: null,
  receipt: null,
  failure: null,
  operationId: null,
});
