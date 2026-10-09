import type { Failure } from "../../../../core/domain/result";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { BackgroundJob } from "../entities/background-job";
import type { JobSearch } from "../entities/job-search";

export const isJobId = (id: string): boolean =>
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/iu.test(id);

export function validateJobSearch(search: JobSearch): Failure | null {
  if (
    (search.beforeAt === null) !== (search.beforeId === null) ||
    (search.beforeAt !== null && utcInstantMicroseconds(search.beforeAt) === null) ||
    (search.beforeId !== null && !isJobId(search.beforeId))
  )
    return { code: "invalid_pagination", fields: {}, parameters: {} };
  return null;
}

export const canRequestJobCancellation = (job: BackgroundJob): boolean =>
  (job.status === "QUEUED" || job.status === "RUNNING") &&
  !job.cancellationRequested &&
  job.availableActions.includes("cancel");
