import type { Failure } from "../../../../core/domain/result";
import type { ApprovalAssigneePage } from "../../domain/entities/approval-assignee";

export type ApprovalAssigneePickerState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  query: string;
  after: string | null;
  page: ApprovalAssigneePage | null;
  failure: Failure | null;
}>;
export const initialApprovalAssigneePickerState: ApprovalAssigneePickerState = Object.freeze({
  stage: "loading",
  query: "",
  after: null,
  page: null,
  failure: null,
});
