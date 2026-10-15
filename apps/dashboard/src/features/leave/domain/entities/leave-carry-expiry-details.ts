import type { LeaveCarryExpiry, LeaveCarryExpiryStatus } from "./leave-carry-expiry";
import type { LeaveCarryExpiryResult } from "./leave-carry-expiry-result";
export type LeaveCarryExpiryDetails = Readonly<{
  expiry: LeaveCarryExpiry;
  attempts: readonly Readonly<{
    jobId: string;
    number: number;
    baseCompleted: number;
    startedAt: string;
    reason: string;
  }>[];
  job: Readonly<{
    id: string;
    status: LeaveCarryExpiryStatus;
    completedItems: number;
    totalItems: number;
    attempts: number;
    version: number;
    failureCode: string | null;
    scheduledFor: string;
    availableAt: string;
    finishedAt: string | null;
  }>;
  results: Readonly<{ items: readonly LeaveCarryExpiryResult[]; nextCursor: string | null }>;
  actions: readonly "RESUME"[];
}>;
