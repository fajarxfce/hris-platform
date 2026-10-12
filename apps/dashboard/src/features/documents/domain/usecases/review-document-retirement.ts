import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canManageDocumentRetention } from "../policies/document-retention-policy";
import type { DocumentRetentionRepository } from "../repositories/document-retention-repository";

export class ReviewDocumentRetirement {
  constructor(private readonly retention: DocumentRetentionRepository) {}
  async execute(
    access: CompanyAccess,
    documentId: string,
    revisionId: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManageDocumentRetention(access.permissions)) return failed("access_denied");
    if (!isUuid(documentId) || !isUuid(revisionId)) return failed("document_revision_not_found");
    const result = await this.retention.retirement(
      access.companyId,
      documentId.toLowerCase(),
      revisionId.toLowerCase(),
      signal,
    );
    signal.throwIfAborted();
    return result;
  }
}
