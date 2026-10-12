import type { OperationId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  DocumentRetentionPolicyFields,
  DocumentRetentionPolicySelection,
} from "../entities/document-retention-policy";
import { prepareDocumentRetentionPolicy } from "../policies/document-retention-policy";
import type { DocumentRetentionRepository } from "../repositories/document-retention-repository";

export class SaveDocumentRetentionPolicy {
  constructor(private readonly retention: DocumentRetentionRepository) {}
  async execute(
    access: CompanyAccess,
    operation: OperationId,
    selection: DocumentRetentionPolicySelection,
    policyId: string,
    fields: DocumentRetentionPolicyFields,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    const input = prepareDocumentRetentionPolicy(access, operation, selection, policyId, fields);
    if (!input.ok) return input;
    const result = await this.retention.savePolicy(
      access.companyId,
      operation,
      input.value,
      signal,
    );
    signal.throwIfAborted();
    return result;
  }
}
