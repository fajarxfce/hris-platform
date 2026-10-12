import type { Failure } from "../../../../core/domain/result";
import type { DocumentRevision } from "../../domain/entities/document-revision";
import type { DocumentUploadReview } from "../../domain/entities/document-upload";

export type DocumentUploadState = Readonly<{
  stage:
    | "loading"
    | "editing"
    | "selecting"
    | "uploading"
    | "paused"
    | "conflict"
    | "complete"
    | "unavailable";
  review: DocumentUploadReview | null;
  file: Readonly<{ name: string; byteLength: number }> | null;
  uploadedBytes: number | null;
  failure: Failure | null;
  completion: DocumentRevision | null;
}>;
export const initialDocumentUploadState: DocumentUploadState = Object.freeze({
  stage: "loading",
  review: null,
  file: null,
  uploadedBytes: null,
  failure: null,
  completion: null,
});
