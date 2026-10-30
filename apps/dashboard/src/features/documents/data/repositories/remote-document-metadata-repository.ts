import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { DocumentMetadataChange } from "../../domain/entities/document-metadata";
import type { DocumentMetadataRepository } from "../../domain/repositories/document-metadata-repository";
import type { DocumentMetadataDataSource } from "../datasources/document-metadata-data-source";
import { toDocumentMetadataPage } from "../mappers/document-metadata-mapper";
import { toDocumentReceipt } from "../mappers/document-mutation-mapper";

export class RemoteDocumentMetadataRepository implements DocumentMetadataRepository {
  constructor(private readonly source: DocumentMetadataDataSource) {}
  history(company: CompanyId, documentId: string, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toDocumentMetadataPage(
        await this.source.history(company, documentId, after, signal),
        company,
        documentId,
        after,
      ),
    );
  }
  save(
    company: CompanyId,
    operation: OperationId,
    input: DocumentMetadataChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toDocumentReceipt(
        await this.source.save(
          company,
          input.documentId,
          operation,
          {
            expectedVersion: input.expectedVersion,
            metadata: { ...input.metadata },
            reason: input.reason,
          },
          signal,
        ),
        input.documentId,
        input.expectedVersion + 1,
      ),
    );
  }
}
