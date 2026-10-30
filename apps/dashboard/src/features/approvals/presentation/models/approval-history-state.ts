import type { Failure } from "../../../../core/domain/result";
import type { ApprovalHistory } from "../../domain/entities/approval-history";
export type ApprovalHistoryState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  history: ApprovalHistory | null;
  after: number | null;
  failure: Failure | null;
}>;
export const initialApprovalHistoryState: ApprovalHistoryState = {
  stage: "loading",
  history: null,
  after: null,
  failure: null,
};
