import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canCreateCompanyDocument } from "../policies/document-upload-policy";
import type { DocumentRepository } from "../repositories/document-repository";

export class ReviewDocumentMetadata {
  constructor(private readonly documents: DocumentRepository) {}
  async execute(access: CompanyAccess, documentId: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canCreateCompanyDocument(access.permissions)) return failed("access_denied");
    if (!isUuid(documentId)) return failed("document_not_found");
    const found = await this.documents.get(access.companyId, documentId.toLowerCase(), signal);
    signal.throwIfAborted();
    if (!found.ok) return found;
    if (
      !found.value.metadataSupported ||
      !found.value.metadata ||
      found.value.metadataVersion == null
    )
      return failed("document_metadata_unsupported");
    if (found.value.metadataVersion >= 999) return failed("document_metadata_limit");
    return found;
  }
}
