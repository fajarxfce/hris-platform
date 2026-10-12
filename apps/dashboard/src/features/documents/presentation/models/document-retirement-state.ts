import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { DocumentRetirementReview } from "../../domain/entities/document-retirement";

export type DocumentRetirementState = Readonly<{
  stage:
    | "loading"
    | "reviewing"
    | "blocked"
    | "submitting"
    | "unconfirmed"
    | "conflict"
    | "saved"
    | "unavailable";
  review: DocumentRetirementReview | null;
  failure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: string | null;
}>;
export const initialDocumentRetirementState: DocumentRetirementState = Object.freeze({
  stage: "loading",
  review: null,
  failure: null,
  receipt: null,
  operationId: null,
});
