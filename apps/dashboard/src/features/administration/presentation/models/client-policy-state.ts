import type { Failure } from "../../../../core/domain/result";
import type { ClientPolicyReview } from "../../domain/entities/client-policy-review";

export type ClientPolicyState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  review: ClientPolicyReview | null;
  failure: Failure | null;
}>;

export const initialClientPolicyState: ClientPolicyState = Object.freeze({
  stage: "idle",
  review: null,
  failure: null,
});
