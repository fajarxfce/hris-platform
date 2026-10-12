import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentRevisionIntent } from "../entities/document-revision-action";
import {
  canPerformDocumentAction,
  hasDocumentManagement,
} from "../policies/document-action-policy";
import { canBrowseDocuments } from "../policies/document-read-policy";
import type { DocumentRepository } from "../repositories/document-repository";

export class ReviewDocumentAction {
  constructor(private readonly documents: DocumentRepository) {}
  async execute(
    access: CompanyAccess,
    documentId: string,
    revisionId: string,
    intent: DocumentRevisionIntent,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canBrowseDocuments(access.permissions) || !hasDocumentManagement(access.permissions))
      return failed("access_denied");
    if (!isUuid(documentId) || !isUuid(revisionId)) return failed("document_revision_not_found");
    const result = await this.documents.revision(
      access.companyId,
      revisionId.toLowerCase(),
      signal,
    );
    signal.throwIfAborted();
    if (!result.ok) return result;
    if (result.value.documentId !== documentId.toLowerCase())
      return failed("document_revision_not_found");
    return canPerformDocumentAction(access, result.value, intent)
      ? result
      : failed("document_action_unavailable");
  }
}
