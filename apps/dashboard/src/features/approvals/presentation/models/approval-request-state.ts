import type { Failure } from "../../../../core/domain/result";
import type { ApprovalRequest } from "../../domain/entities/approval-request";

export type ApprovalRequestState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  request: ApprovalRequest | null;
  failure: Failure | null;
}>;
export const initialApprovalRequestState: ApprovalRequestState = Object.freeze({
  stage: "loading",
  request: null,
  failure: null,
});
