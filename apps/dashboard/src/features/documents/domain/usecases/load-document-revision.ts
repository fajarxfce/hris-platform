import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentRevisionReview } from "../entities/document-revision-review";
import { canBrowseDocuments } from "../policies/document-read-policy";
import type { DocumentRepository } from "../repositories/document-repository";

export class LoadDocumentRevision {
  constructor(private readonly documents: DocumentRepository) {}
  async execute(
    access: CompanyAccess,
    documentId: string,
    revisionId: string,
    signal: AbortSignal,
  ): Promise<Result<DocumentRevisionReview>> {
    signal.throwIfAborted();
    if (!canBrowseDocuments(access.permissions)) return failed("access_denied");
    if (!isUuid(documentId) || !isUuid(revisionId)) return failed("document_revision_not_found");
    const found = await this.documents.revision(access.companyId, revisionId.toLowerCase(), signal);
    signal.throwIfAborted();
    if (!found.ok) return found;
    if (found.value.documentId !== documentId.toLowerCase())
      return failed("document_revision_not_found");
    const attempts = await this.documents.validationAttempts(
      access.companyId,
      found.value.id,
      signal,
    );
    signal.throwIfAborted();
    return attempts.ok
      ? { ok: true, value: Object.freeze({ revision: found.value, attempts: attempts.value }) }
      : attempts;
  }
}
