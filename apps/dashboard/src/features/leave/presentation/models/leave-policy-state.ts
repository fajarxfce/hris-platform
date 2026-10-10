import type { Failure } from "../../../../core/domain/result";
import type { LeavePolicyReview } from "../../domain/entities/leave-policy-review";
import type { LeavePolicyRevision } from "../../domain/entities/leave-policy-revision";

export type LeavePolicyState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  review: LeavePolicyReview | null;
  selectedRevision: LeavePolicyRevision | null;
  failure: Failure | null;
}>;
export const initialLeavePolicyState: LeavePolicyState = {
  stage: "loading",
  review: null,
  selectedRevision: null,
  failure: null,
};
