import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type {
  DocumentRetentionPolicy,
  DocumentRetentionPolicyChange,
} from "../../domain/entities/document-retention-policy";
import type { DocumentRetentionRepository } from "../../domain/repositories/document-retention-repository";
import type { DocumentRetentionDataSource } from "../datasources/document-retention-data-source";
import {
  toDocumentRetentionPolicies,
  toDocumentRetentionPolicyHistory,
  toDocumentRetentionReceipt,
} from "../mappers/document-retention-mapper";

export class RemoteDocumentRetentionRepository implements DocumentRetentionRepository {
  constructor(private readonly source: DocumentRetentionDataSource) {}
  policies(company: CompanyId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toDocumentRetentionPolicies(await this.source.policies(company, signal), company),
    );
  }
  policyHistory(
    company: CompanyId,
    policy: DocumentRetentionPolicy,
    after: string | null,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toDocumentRetentionPolicyHistory(
        await this.source.policyHistory(company, policy.id, after, signal),
        company,
        policy,
        after,
      ),
    );
  }
  savePolicy(
    company: CompanyId,
    operation: OperationId,
    input: DocumentRetentionPolicyChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toDocumentRetentionReceipt(
        await this.source.savePolicy(company, operation, { ...input }, signal),
        input,
      ),
    );
  }
}
