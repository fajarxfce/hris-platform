import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentRetentionIntent } from "../entities/document-retention";
import {
  availableDocumentRetentionIntents,
  validDocumentRetentionIntent,
} from "../policies/document-retention-change-policy";
import { canManageDocumentRetention } from "../policies/document-retention-policy";
import type { DocumentRetentionRepository } from "../repositories/document-retention-repository";

export class ReviewDocumentRetentionChange {
  constructor(private readonly retention: DocumentRetentionRepository) {}
  async execute(
    access: CompanyAccess,
    documentId: string,
    intent: DocumentRetentionIntent,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManageDocumentRetention(access.permissions)) return failed("access_denied");
    if (!isUuid(documentId)) return failed("document_not_found");
    if (!validDocumentRetentionIntent(intent)) return failed("invalid_document_retention_change");
    const result = await this.retention.state(access.companyId, documentId.toLowerCase(), signal);
    signal.throwIfAborted();
    if (!result.ok) return result;
    return availableDocumentRetentionIntents(result.value).includes(intent)
      ? result
      : failed("document_retention_action_unavailable");
  }
}
