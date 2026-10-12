import type { DocumentRevision } from "./document-revision";
import type { DocumentValidationAttempt } from "./document-validation-attempt";

export type DocumentRevisionReview = Readonly<{
  revision: DocumentRevision;
  attempts: readonly DocumentValidationAttempt[];
}>;
