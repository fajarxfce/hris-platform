import type { AccountId } from "../../../../core/domain/identifiers";
import type { ApprovalRequest } from "./approval-request";
export type ApprovalDecisionEntry = Readonly<{
  step: number;
  actorId: AccountId;
  decidingFor: AccountId;
  decision: "APPROVE" | "REJECT";
  reason: string;
  decidedAt: string;
}>;
export type ApprovalReassignmentEntry = Readonly<{
  revision: number;
  step: number;
  assignees: readonly AccountId[];
  actorId: AccountId;
  reason: string;
  recordedAt: string;
}>;
export type ApprovalHistory = Readonly<{
  request: ApprovalRequest;
  decisions: readonly ApprovalDecisionEntry[];
  reassignments: Readonly<{
    items: readonly ApprovalReassignmentEntry[];
    nextCursor: string | null;
  }>;
}>;
