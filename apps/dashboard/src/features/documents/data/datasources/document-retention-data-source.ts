import type {
  DocumentRetentionPolicyChangeDto,
  DocumentRetentionPolicyDto,
  DocumentRetentionPolicyPageDto,
  DocumentRetentionReceiptDto,
} from "../models/document-retention-dto";
import type {
  DocumentRetentionCommandDto,
  DocumentRetentionDto,
  DocumentRetentionHistoryDto,
} from "../models/document-retention-state-dto";
import type {
  DocumentRetirementCommandDto,
  DocumentRetirementReceiptDto,
  DocumentRetirementReviewDto,
} from "../models/document-retirement-dto";

export interface DocumentRetentionDataSource {
  retirement(
    company: string,
    revisionId: string,
    signal: AbortSignal,
  ): Promise<DocumentRetirementReviewDto>;
  retire(
    company: string,
    operation: string,
    input: DocumentRetirementCommandDto,
    signal: AbortSignal,
  ): Promise<DocumentRetirementReceiptDto>;
  state(company: string, documentId: string, signal: AbortSignal): Promise<DocumentRetentionDto>;
  history(
    company: string,
    documentId: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<DocumentRetentionHistoryDto>;
  change(
    company: string,
    operation: string,
    input: DocumentRetentionCommandDto,
    signal: AbortSignal,
  ): Promise<DocumentRetentionReceiptDto>;
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
