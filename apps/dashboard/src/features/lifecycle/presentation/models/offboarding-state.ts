import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { OffboardingReview } from "../../domain/entities/offboarding-review";

export type OffboardingState = Readonly<{
  stage:
    | "loading"
    | "unavailable"
    | "reviewing"
    | "submitting"
    | "unconfirmed"
    | "conflict"
    | "completed";
  review: OffboardingReview | null;
  failure: Failure | null;
  operationId: string | null;
  receipt: MutationReceipt | null;
}>;
export const initialOffboardingState: OffboardingState = Object.freeze({
  stage: "loading",
  review: null,
  failure: null,
  operationId: null,
  receipt: null,
});
