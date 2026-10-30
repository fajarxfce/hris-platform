import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { Document } from "../entities/document";
import type { DocumentMetadataFields } from "../entities/document-metadata";
import { prepareDocumentMetadata } from "../policies/document-metadata-policy";
import type { DocumentMetadataRepository } from "../repositories/document-metadata-repository";

export class SaveDocumentMetadata {
  constructor(private readonly metadata: DocumentMetadataRepository) {}
  async execute(
    access: CompanyAccess,
    operation: OperationId,
    document: Document,
    fields: DocumentMetadataFields,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const input = prepareDocumentMetadata(access, operation, document, fields);
    if (!input.ok) return input;
    const result = await this.metadata.save(access.companyId, operation, input.value, signal);
    signal.throwIfAborted();
    return result;
  }
}
