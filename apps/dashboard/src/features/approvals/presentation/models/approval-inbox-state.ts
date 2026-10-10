import type { Failure } from "../../../../core/domain/result";
import type { ApprovalInbox } from "../../domain/entities/approval-request";

export type ApprovalInboxState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: ApprovalInbox | null;
  failure: Failure | null;
}>;
export const initialApprovalInboxState: ApprovalInboxState = Object.freeze({
  stage: "loading",
  page: null,
  failure: null,
});
