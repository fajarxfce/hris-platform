import type { Failure } from "../../../../core/domain/result";
import type { BackgroundJob } from "../../domain/entities/background-job";

export type JobDetailState = Readonly<
  | { stage: "idle" | "loading"; job: null; failure: null }
  | { stage: "ready" | "confirming" | "cancelling"; job: BackgroundJob; failure: null }
  | { stage: "unavailable" | "unconfirmed"; job: null; failure: Failure }
>;
export const initialJobDetailState: JobDetailState = Object.freeze({
  stage: "idle",
  job: null,
  failure: null,
});
