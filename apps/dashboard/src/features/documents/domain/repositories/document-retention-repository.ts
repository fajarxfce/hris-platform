import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type {
  DocumentRetentionPolicy,
  DocumentRetentionPolicyChange,
  DocumentRetentionPolicyPage,
} from "../entities/document-retention-policy";

export interface DocumentRetentionRepository {
  policies(
    company: CompanyId,
    signal: AbortSignal,
  ): Promise<Result<readonly DocumentRetentionPolicy[]>>;
  policyHistory(
    company: CompanyId,
    policy: DocumentRetentionPolicy,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<DocumentRetentionPolicyPage>>;
  savePolicy(
    company: CompanyId,
    operation: OperationId,
    input: DocumentRetentionPolicyChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
