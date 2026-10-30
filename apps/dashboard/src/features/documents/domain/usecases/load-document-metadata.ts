import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentMetadataReview } from "../entities/document-metadata";
import { sameDocumentMetadata } from "../policies/document-business-metadata-policy";
import { validDocumentMetadataCursor } from "../policies/document-metadata-policy";
import { canBrowseDocuments } from "../policies/document-read-policy";
import type { DocumentMetadataRepository } from "../repositories/document-metadata-repository";
import type { DocumentRepository } from "../repositories/document-repository";

export class LoadDocumentMetadata {
  constructor(
    private readonly documents: DocumentRepository,
    private readonly metadata: DocumentMetadataRepository,
  ) {}
  async execute(
    access: CompanyAccess,
    documentId: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<DocumentMetadataReview>> {
    signal.throwIfAborted();
    if (!canBrowseDocuments(access.permissions)) return failed("access_denied");
    if (!isUuid(documentId)) return failed("document_not_found");
    if (!validDocumentMetadataCursor(after)) return failed("invalid_document_page");
    const id = documentId.toLowerCase();
    const found = await this.documents.get(access.companyId, id, signal);
    signal.throwIfAborted();
    if (!found.ok) return found;
    const document = found.value;
    if (!document.metadataSupported || !document.metadata || document.metadataVersion == null)
      return failed("document_metadata_unsupported");
    const history = await this.metadata.history(access.companyId, id, after, signal);
    signal.throwIfAborted();
    if (!history.ok) return history;
    const remaining = Math.max(0, document.metadataVersion - (after === null ? -1 : Number(after)));
    const rows = history.value.items;
    const last = rows.at(-1);
    if (
      rows.length !== Math.min(20, remaining) ||
      remaining > 20 !== (history.value.nextCursor !== null) ||
      rows.some((row) => row.documentVersion > document.version) ||
      (last?.version === document.metadataVersion &&
        !sameDocumentMetadata(last.metadata, document.metadata))
    )
      return failed("document_metadata_changed");
    return success(Object.freeze({ document, history: history.value }));
  }
}
