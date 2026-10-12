import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentReview } from "../entities/document-review";
import { canBrowseDocuments, validDocumentRevisionCursor } from "../policies/document-read-policy";
import type { DocumentRepository } from "../repositories/document-repository";

export class LoadDocumentReview {
  constructor(private readonly documents: DocumentRepository) {}
  async execute(
    access: CompanyAccess,
    documentId: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<DocumentReview>> {
    signal.throwIfAborted();
    if (!canBrowseDocuments(access.permissions)) return failed("access_denied");
    if (!isUuid(documentId)) return failed("document_not_found");
    if (!validDocumentRevisionCursor(after)) return failed("invalid_document_page");
    const id = documentId.toLowerCase();
    const found = await this.documents.get(access.companyId, id, signal);
    signal.throwIfAborted();
    if (!found.ok) return found;
    const history = await this.documents.revisions(access.companyId, id, after, signal);
    signal.throwIfAborted();
    return history.ok
      ? { ok: true, value: Object.freeze({ document: found.value, revisions: history.value }) }
      : history;
  }
}
