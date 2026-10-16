import type { Failure } from "../../../../core/domain/result";
import type { ManagedAccountReview } from "../../domain/entities/managed-account-review";
export type ManagedAccountState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  review: ManagedAccountReview | null;
  failure: Failure | null;
}>;
export const initialManagedAccountState: ManagedAccountState = Object.freeze({
  stage: "loading",
  review: null,
  failure: null,
});
