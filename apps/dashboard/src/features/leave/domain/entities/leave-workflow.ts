import type { AccountId } from "../../../../core/domain/identifiers";
import type {
  ApprovalId,
  ApprovalStatus,
} from "../../../approvals/domain/entities/approval-request";

export type LeaveWorkflow = Readonly<{
  id: ApprovalId;
  authorId: AccountId;
  requesterId: AccountId | null;
  status: ApprovalStatus;
  currentStep: number;
  stages: readonly (readonly AccountId[])[];
  version: number;
}>;
