import type {
  DocumentMetadataChangeDto,
  DocumentMetadataPageDto,
} from "../models/document-metadata-dto";
import type { DocumentMutationReceiptDto } from "../models/document-mutation-dto";

export interface DocumentMetadataDataSource {
  history(
    company: string,
    documentId: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<DocumentMetadataPageDto>;
  save(
    company: string,
    documentId: string,
    operation: string,
    body: DocumentMetadataChangeDto,
    signal: AbortSignal,
  ): Promise<DocumentMutationReceiptDto>;
}
