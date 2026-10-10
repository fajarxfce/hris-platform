import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { ApprovalTemplate } from "../../domain/entities/approval-template";

export type ApprovalTemplateEditorState = Readonly<{
  stage: "loading" | "editing" | "saving" | "unconfirmed" | "conflict" | "saved" | "unavailable";
  template: ApprovalTemplate | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialApprovalTemplateEditorState: ApprovalTemplateEditorState = Object.freeze({
  stage: "loading",
  template: null,
  failure: null,
  receipt: null,
  operationId: null,
});
