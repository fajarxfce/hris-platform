import type { LoadDocumentMetadata } from "../../domain/usecases/load-document-metadata";
import type { ReviewDocumentMetadata } from "../../domain/usecases/review-document-metadata";
import type { SaveDocumentMetadata } from "../../domain/usecases/save-document-metadata";

export type DocumentMetadataUseCases = Readonly<{
  load: Pick<LoadDocumentMetadata, "execute">;
  review: Pick<ReviewDocumentMetadata, "execute">;
  save: Pick<SaveDocumentMetadata, "execute">;
}>;
