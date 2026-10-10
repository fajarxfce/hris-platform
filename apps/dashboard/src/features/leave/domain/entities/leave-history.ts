import type { AccountId } from "../../../../core/domain/identifiers";
import type { LeaveStatus } from "./leave-request";

export const leaveChangeKinds = [
  "SUBMITTED",
  "DECIDED",
  "WITHDRAWN",
  "CANCELLATION_REQUESTED",
] as const;
export type LeaveChange = Readonly<{
  version: number;
  kind: (typeof leaveChangeKinds)[number];
  status: LeaveStatus;
  cancellationApprovalId: string | null;
  actorId: AccountId;
  recordedAt: string;
  reason: string;
}>;
export type LeaveHistory = Readonly<{ items: readonly LeaveChange[]; nextCursor: string | null }>;
