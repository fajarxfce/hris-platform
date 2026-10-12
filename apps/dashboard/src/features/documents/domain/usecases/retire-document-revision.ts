import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentRetirementReview } from "../entities/document-retirement";
import { prepareDocumentRetirement } from "../policies/document-retirement-policy";
import type { DocumentRetentionRepository } from "../repositories/document-retention-repository";

export class RetireDocumentRevision {
  constructor(private readonly retention: DocumentRetentionRepository) {}
  async execute(
    access: CompanyAccess,
    operation: OperationId,
    review: DocumentRetirementReview,
    reason: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const input = prepareDocumentRetirement(access, operation, review, reason);
    if (!input.ok) return input;
    const result = await this.retention.retire(access.companyId, operation, input.value, signal);
    signal.throwIfAborted();
    return result;
  }
}
