import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentRetentionPolicySelection } from "../entities/document-retention-policy";
import {
  canManageDocumentRetention,
  validRetentionClassification,
} from "../policies/document-retention-policy";
import type { DocumentRetentionRepository } from "../repositories/document-retention-repository";

export class ReviewDocumentRetentionPolicy {
  constructor(private readonly retention: DocumentRetentionRepository) {}
  async execute(
    access: CompanyAccess,
    classification: string,
    signal: AbortSignal,
  ): Promise<Result<DocumentRetentionPolicySelection>> {
    signal.throwIfAborted();
    if (!canManageDocumentRetention(access.permissions)) return failed("access_denied");
    if (!validRetentionClassification(classification))
      return failed("invalid_document_retention_policy");
    const result = await this.retention.policies(access.companyId, signal);
    signal.throwIfAborted();
    if (!result.ok) return result;
    return success(
      Object.freeze({
        companyId: access.companyId,
        classification,
        policy: result.value.find((policy) => policy.classification === classification) ?? null,
      }),
    );
  }
}
