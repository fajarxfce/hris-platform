import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { LifecycleTemplate } from "../../domain/entities/lifecycle-template";

export type LifecycleTemplateEditorState = Readonly<{
  stage: "loading" | "editing" | "saving" | "unconfirmed" | "conflict" | "saved" | "unavailable";
  template: LifecycleTemplate | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialLifecycleTemplateEditorState: LifecycleTemplateEditorState = Object.freeze({
  stage: "loading",
  template: null,
  failure: null,
  receipt: null,
  operationId: null,
});
