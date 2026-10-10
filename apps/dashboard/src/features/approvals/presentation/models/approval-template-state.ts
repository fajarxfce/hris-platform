import type { Failure } from "../../../../core/domain/result";
import type { ApprovalTemplate } from "../../domain/entities/approval-template";

export type ApprovalTemplateState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  template: ApprovalTemplate | null;
  failure: Failure | null;
}>;
export const initialApprovalTemplateState: ApprovalTemplateState = Object.freeze({
  stage: "loading",
  template: null,
  failure: null,
});
