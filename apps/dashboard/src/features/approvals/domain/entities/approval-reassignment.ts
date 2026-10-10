import type { AccountId } from "../../../../core/domain/identifiers";
import type { ApprovalId } from "./approval-request";

export type ApprovalReassignmentSelection = Readonly<{
  assignees: readonly AccountId[];
  reason: string;
}>;
export type ApprovalReassignment = ApprovalReassignmentSelection &
  Readonly<{ id: ApprovalId; version: number }>;
