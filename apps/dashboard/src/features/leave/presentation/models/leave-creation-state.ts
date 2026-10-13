import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { LeaveEvidenceOption } from "../../domain/entities/leave-evidence-option";
import type { LeavePreparationEmployee } from "../../domain/entities/leave-preparation-employee";
import type { ReviewedLeaveSelection } from "../../domain/entities/leave-request-preview";
import type { LeaveRequestType } from "../../domain/entities/leave-request-type";

export type LeaveCreationState = Readonly<{
  stage:
    | "editing"
    | "previewing"
    | "reviewing"
    | "submitting"
    | "unconfirmed"
    | "saved"
    | "unavailable";
  employee: LeavePreparationEmployee | null;
  type: LeaveRequestType | null;
  review: ReviewedLeaveSelection | null;
  evidence: readonly LeaveEvidenceOption[];
  failure: Failure | null;
  operationId: OperationId | null;
  receipt: MutationReceipt | null;
}>;

export const initialLeaveCreationState: LeaveCreationState = Object.freeze({
  stage: "editing",
  employee: null,
  type: null,
  review: null,
  evidence: Object.freeze([]),
  failure: null,
  operationId: null,
  receipt: null,
});

export const leaveCreationIsLocked = (state: LeaveCreationState): boolean =>
  ["submitting", "unconfirmed", "saved"].includes(state.stage);
