import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentRetention, DocumentRetentionIntent } from "../entities/document-retention";
import { prepareDocumentRetentionChange } from "../policies/document-retention-change-policy";
import type { DocumentRetentionRepository } from "../repositories/document-retention-repository";

export class ChangeDocumentRetention {
  constructor(private readonly retention: DocumentRetentionRepository) {}
  async execute(
    access: CompanyAccess,
    operation: OperationId,
    state: DocumentRetention,
    intent: DocumentRetentionIntent,
    reason: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const input = prepareDocumentRetentionChange(access, operation, state, intent, reason);
    if (!input.ok) return input;
    const result = await this.retention.change(access.companyId, operation, input.value, signal);
    signal.throwIfAborted();
    return result;
  }
}
