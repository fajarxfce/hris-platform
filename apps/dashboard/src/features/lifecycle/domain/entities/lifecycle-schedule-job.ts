import type { JobId, JobStatus } from "../../../jobs/domain/entities/background-job";
export type LifecycleScheduleJob = Readonly<{
  id: JobId;
  status: JobStatus;
  version: number;
  attempts: number;
  cancellationRequested: boolean;
  completedItems: number;
  failureCode: string | null;
  finishedAt: string | null;
}>;
