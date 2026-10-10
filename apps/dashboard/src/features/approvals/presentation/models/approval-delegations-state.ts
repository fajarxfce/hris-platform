import type { Failure } from "../../../../core/domain/result";
import type { ApprovalDelegationPage } from "../../domain/entities/approval-delegation";

export type ApprovalDelegationsState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: ApprovalDelegationPage | null;
  failure: Failure | null;
}>;
export const initialApprovalDelegationsState: ApprovalDelegationsState = Object.freeze({
  stage: "loading",
  page: null,
  failure: null,
});
