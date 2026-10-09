import type { LoadJob } from "../../domain/usecases/load-job";
import type { LoadJobs } from "../../domain/usecases/load-jobs";
import type { RequestJobCancellation } from "../../domain/usecases/request-job-cancellation";

export type JobsUseCases = Readonly<{
  loadJobs: Pick<LoadJobs, "execute">;
  loadJob: Pick<LoadJob, "execute">;
  requestCancellation: Pick<RequestJobCancellation, "execute">;
}>;
