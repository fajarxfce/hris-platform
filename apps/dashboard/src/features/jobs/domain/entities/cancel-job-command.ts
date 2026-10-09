import type { JobId } from "./background-job";

export type CancelJobCommand = Readonly<{ jobId: JobId; expectedVersion: number }>;
