import type { AccountId } from "../../../../core/domain/identifiers";
import type { OvertimeInterval } from "./overtime-interval";
import type { OvertimeStatus } from "./overtime-request";

export const overtimeChangeKinds = ["PLANNED", "ACTUAL_SUBMITTED", "DECIDED", "WITHDRAWN"] as const;
export type OvertimeChange = Readonly<{
  revision: number;
  kind: (typeof overtimeChangeKinds)[number];
  status: OvertimeStatus;
  actual: OvertimeInterval | null;
  approvedMinutes: number;
  approvalId: string | null;
  actorId: AccountId;
  recordedAt: string;
  reason: string;
}>;
