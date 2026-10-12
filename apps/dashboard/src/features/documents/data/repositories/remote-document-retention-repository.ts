import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { DocumentRetentionCommand } from "../../domain/entities/document-retention";
import type {
  DocumentRetentionPolicy,
  DocumentRetentionPolicyChange,
} from "../../domain/entities/document-retention-policy";
import type { DocumentRetirementCommand } from "../../domain/entities/document-retirement";
import type { DocumentRetentionRepository } from "../../domain/repositories/document-retention-repository";
import type { DocumentRetentionDataSource } from "../datasources/document-retention-data-source";
import {
  toDocumentRetentionPolicies,
  toDocumentRetentionPolicyHistory,
  toDocumentRetentionReceipt,
} from "../mappers/document-retention-mapper";
import {
  toDocumentRetention,
  toDocumentRetentionChangeReceipt,
  toDocumentRetentionHistory,
} from "../mappers/document-retention-state-mapper";
import {
  toDocumentRetirementReceipt,
  toDocumentRetirementReview,
} from "../mappers/document-retirement-mapper";

export class RemoteDocumentRetentionRepository implements DocumentRetentionRepository {
  constructor(private readonly source: DocumentRetentionDataSource) {}
  retirement(company: CompanyId, documentId: string, revisionId: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toDocumentRetirementReview(
        await this.source.retirement(company, revisionId, signal),
        company,
        documentId,
        revisionId,
      ),
    );
  }
  retire(
    company: CompanyId,
    operation: OperationId,
    input: DocumentRetirementCommand,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toDocumentRetirementReceipt(
        await this.source.retire(
          company,
          operation,
          {
            revisionId: input.revisionId,
            expectedRevisionVersion: input.expectedRevisionVersion,
            expectedRetentionVersion: input.expectedRetentionVersion,
            reason: input.reason,
          },
          signal,
        ),
        input,
      ),
    );
  }
  state(company: CompanyId, documentId: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toDocumentRetention(
        await this.source.state(company, documentId, signal),
        company,
        documentId,
      ),
    );
  }
  history(company: CompanyId, documentId: string, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toDocumentRetentionHistory(
        await this.source.history(company, documentId, after, signal),
        company,
        documentId,
        after,
      ),
    );
  }
  change(
    company: CompanyId,
    operation: OperationId,
    input: DocumentRetentionCommand,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toDocumentRetentionChangeReceipt(
        await this.source.change(company, operation, { ...input }, signal),
        input,
      ),
    );
  }
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
