import type { Failure } from "../../../../core/domain/result";
import type { ApprovalDelegation } from "../../domain/entities/approval-delegation";

export type ApprovalDelegationState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  delegation: ApprovalDelegation | null;
  failure: Failure | null;
}>;
export const initialApprovalDelegationState: ApprovalDelegationState = Object.freeze({
  stage: "loading",
  delegation: null,
  failure: null,
});
