import type { JobId, JobStatus } from "../../../jobs/domain/entities/background-job";

export type WorkPeriodAttempt = Readonly<{
  id: JobId;
  status: JobStatus;
  totalEmployees: number;
  completedEmployees: number;
  cancellationRequested: boolean;
  failureCode: string | null;
  createdAt: string;
  finishedAt: string | null;
}>;
