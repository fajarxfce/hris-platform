import type {
  DocumentRetentionPolicyChangeDto,
  DocumentRetentionPolicyDto,
  DocumentRetentionPolicyPageDto,
  DocumentRetentionReceiptDto,
} from "../models/document-retention-dto";

export interface DocumentRetentionDataSource {
  policies(company: string, signal: AbortSignal): Promise<DocumentRetentionPolicyDto[]>;
  policyHistory(
    company: string,
    id: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<DocumentRetentionPolicyPageDto>;
  savePolicy(
    company: string,
    operation: string,
    input: DocumentRetentionPolicyChangeDto,
    signal: AbortSignal,
  ): Promise<DocumentRetentionReceiptDto>;
}
