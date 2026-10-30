import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { Document } from "../../domain/entities/document";

export type DocumentMetadataEditorState = Readonly<{
  stage: "loading" | "editing" | "saving" | "unconfirmed" | "conflict" | "saved" | "unavailable";
  selection: Document | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialDocumentMetadataEditorState: DocumentMetadataEditorState = Object.freeze({
  stage: "loading",
  selection: null,
  failure: null,
  receipt: null,
  operationId: null,
});
