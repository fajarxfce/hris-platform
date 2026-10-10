import type { LeaveRequestId } from "./leave-request";
import type { LeaveRequestDetails } from "./leave-request-details";

export const leaveRequestIntents = ["approve", "reject", "withdraw", "cancel"] as const;
export type LeaveRequestIntent = (typeof leaveRequestIntents)[number];
export type LeaveActionSnapshot = Pick<
  LeaveRequestDetails,
  "id" | "companyId" | "version" | "status" | "availableActions"
>;
export type LeaveRequestChange = Readonly<{
  id: LeaveRequestId;
  version: number;
  reason: string;
}>;
export type LeaveRequestDecision = LeaveRequestChange &
  Readonly<{ decision: "APPROVE" | "REJECT" }>;
