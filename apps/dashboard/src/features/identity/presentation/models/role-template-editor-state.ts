import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { RoleTemplate } from "../../domain/entities/role-template";
export type RoleTemplateEditorState = Readonly<{
  stage: "loading" | "editing" | "saving" | "unconfirmed" | "conflict" | "saved" | "unavailable";
  template: RoleTemplate | null;
  permissions: readonly string[];
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialRoleTemplateEditorState: RoleTemplateEditorState = Object.freeze({
  stage: "loading",
  template: null,
  permissions: Object.freeze([]),
  failure: null,
  receipt: null,
  operationId: null,
});
