import type { LeaveBatch } from "./leave-batch";
import type { LeaveBatchResult } from "./leave-batch-result";
export type LeaveBatchDetails = Readonly<{
  batch: LeaveBatch;
  counts: Readonly<{
    applied: number;
    unchanged: number;
    skipped: number;
    failed: number;
    completed: number;
  }>;
  attempts: readonly Readonly<{
    jobId: string;
    number: number;
    baseCompleted: number;
    startedAt: string;
    reason: string;
  }>[];
  job: Readonly<{
    id: string;
    status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
    completedItems: number;
    totalItems: number;
    attempts: number;
    version: number;
    cancellationRequested: boolean;
    failureCode: string | null;
  }>;
  results: Readonly<{ items: readonly LeaveBatchResult[]; nextCursor: string | null }>;
  actions: readonly "RESUME"[];
}>;
