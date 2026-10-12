import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { DocumentRetentionPolicySelection } from "../../domain/entities/document-retention-policy";

export type DocumentRetentionEditorState = Readonly<{
  stage: "loading" | "editing" | "saving" | "unconfirmed" | "conflict" | "saved" | "unavailable";
  selection: DocumentRetentionPolicySelection | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialDocumentRetentionEditorState: DocumentRetentionEditorState = Object.freeze({
  stage: "loading",
  selection: null,
  failure: null,
  receipt: null,
  operationId: null,
});
