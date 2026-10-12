import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { DocumentRevision } from "../../domain/entities/document-revision";

export type DocumentActionState = Readonly<{
  stage:
    | "loading"
    | "reviewing"
    | "submitting"
    | "unconfirmed"
    | "conflict"
    | "saved"
    | "unavailable";
  review: DocumentRevision | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialDocumentActionState: DocumentActionState = Object.freeze({
  stage: "loading",
  review: null,
  failure: null,
  receipt: null,
  operationId: null,
});
