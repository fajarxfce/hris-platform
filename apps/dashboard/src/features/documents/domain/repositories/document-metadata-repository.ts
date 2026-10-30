import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { DocumentMetadataChange, DocumentMetadataPage } from "../entities/document-metadata";

export interface DocumentMetadataRepository {
  history(
    company: CompanyId,
    documentId: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<DocumentMetadataPage>>;
  save(
    company: CompanyId,
    operation: OperationId,
    input: DocumentMetadataChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
