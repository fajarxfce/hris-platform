import type { Failure } from "../../../../core/domain/result";
import type { ApprovalTemplatePage } from "../../domain/entities/approval-template";

export type ApprovalTemplatesState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: ApprovalTemplatePage | null;
  failure: Failure | null;
}>;
export const initialApprovalTemplatesState: ApprovalTemplatesState = Object.freeze({
  stage: "loading",
  page: null,
  failure: null,
});
