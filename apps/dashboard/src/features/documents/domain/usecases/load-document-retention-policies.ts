import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canManageDocumentRetention } from "../policies/document-retention-policy";
import type { DocumentRetentionRepository } from "../repositories/document-retention-repository";

export class LoadDocumentRetentionPolicies {
  constructor(private readonly retention: DocumentRetentionRepository) {}
  async execute(access: CompanyAccess, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canManageDocumentRetention(access.permissions)) return failed("access_denied");
    const result = await this.retention.policies(access.companyId, signal);
    signal.throwIfAborted();
    return result;
  }
}
