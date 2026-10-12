import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { DocumentRetention } from "../../domain/entities/document-retention";

export type DocumentRetentionChangeState = Readonly<{
  stage:
    | "loading"
    | "reviewing"
    | "submitting"
    | "unconfirmed"
    | "conflict"
    | "saved"
    | "unavailable";
  review: DocumentRetention | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialDocumentRetentionChangeState: DocumentRetentionChangeState = Object.freeze({
  stage: "loading",
  review: null,
  failure: null,
  receipt: null,
  operationId: null,
});
