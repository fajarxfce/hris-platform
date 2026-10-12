import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentRetentionReview } from "../entities/document-retention";
import {
  canManageDocumentRetention,
  validRetentionCursor,
} from "../policies/document-retention-policy";
import type { DocumentRetentionRepository } from "../repositories/document-retention-repository";

export class LoadDocumentRetention {
  constructor(private readonly retention: DocumentRetentionRepository) {}
  async execute(
    access: CompanyAccess,
    documentId: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<DocumentRetentionReview>> {
    signal.throwIfAborted();
    if (!canManageDocumentRetention(access.permissions)) return failed("access_denied");
    if (!isUuid(documentId)) return failed("document_not_found");
    if (!validRetentionCursor(after)) return failed("invalid_document_page");
    const id = documentId.toLowerCase();
    const state = await this.retention.state(access.companyId, id, signal);
    signal.throwIfAborted();
    if (!state.ok) return state;
    const history = await this.retention.history(access.companyId, id, after, signal);
    signal.throwIfAborted();
    if (!history.ok) return history;
    if (
      history.value.items.some((entry) => (entry.state.version ?? -1) > (state.value.version ?? -1))
    )
      return failed("document_retention_state_changed");
    return success(Object.freeze({ current: state.value, history: history.value }));
  }
}
