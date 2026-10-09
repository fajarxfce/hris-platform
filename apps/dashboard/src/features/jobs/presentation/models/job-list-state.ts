import type { Failure } from "../../../../core/domain/result";
import type { JobPage } from "../../domain/entities/job-page";

export type JobListState = Readonly<{
  stage: "idle" | "loading" | "ready" | "unavailable";
  page: JobPage | null;
  failure: Failure | null;
}>;
export const initialJobListState: JobListState = Object.freeze({
  stage: "idle",
  page: null,
  failure: null,
});
