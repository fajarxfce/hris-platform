import type { AccountId } from "../../../../core/domain/identifiers";
import type {
  ApprovalId,
  ApprovalStatus,
} from "../../../approvals/domain/entities/approval-request";

export type OvertimeWorkflow = Readonly<{
  id: ApprovalId;
  status: ApprovalStatus;
  currentStep: number;
  stages: readonly (readonly AccountId[])[];
  version: number;
}>;
