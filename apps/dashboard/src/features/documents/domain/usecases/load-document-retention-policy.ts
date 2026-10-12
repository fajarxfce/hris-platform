import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentRetentionPolicyReview } from "../entities/document-retention-policy";
import {
  canManageDocumentRetention,
  validRetentionClassification,
  validRetentionCursor,
} from "../policies/document-retention-policy";
import type { DocumentRetentionRepository } from "../repositories/document-retention-repository";

export class LoadDocumentRetentionPolicy {
  constructor(private readonly retention: DocumentRetentionRepository) {}
  async execute(
    access: CompanyAccess,
    classification: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<DocumentRetentionPolicyReview>> {
    signal.throwIfAborted();
    if (!canManageDocumentRetention(access.permissions)) return failed("access_denied");
    if (!validRetentionClassification(classification))
      return failed("invalid_document_retention_policy");
    if (!validRetentionCursor(after)) return failed("invalid_document_page");
    const result = await this.retention.policies(access.companyId, signal);
    signal.throwIfAborted();
    if (!result.ok) return result;
    const policy = result.value.find((policy) => policy.classification === classification) ?? null;
    const selection = Object.freeze({ companyId: access.companyId, classification, policy });
    if (policy === null)
      return after === null
        ? success(
            Object.freeze({
              selection,
              history: Object.freeze({ items: Object.freeze([]), nextCursor: null }),
            }),
          )
        : failed("document_retention_policy_not_found");
    const history = await this.retention.policyHistory(access.companyId, policy, after, signal);
    signal.throwIfAborted();
    if (!history.ok) return history;
    // Independent live reads cannot label a newer history entry as the reviewed current policy.
    if (history.value.items.some((entry) => entry.version > policy.version))
      return failed("document_retention_review_changed");
    return success(Object.freeze({ selection, history: history.value }));
  }
}
