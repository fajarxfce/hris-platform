import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type DocumentRetentionPolicyChangeDto,
  documentRetentionPoliciesDto,
  documentRetentionPolicyPageDto,
  documentRetentionReceiptDto,
} from "../models/document-retention-dto";
import {
  type DocumentRetentionCommandDto,
  documentRetentionDto,
  documentRetentionHistoryDto,
} from "../models/document-retention-state-dto";
import {
  type DocumentRetirementCommandDto,
  documentRetirementReceiptDto,
  documentRetirementReviewDto,
} from "../models/document-retirement-dto";
import type { DocumentRetentionDataSource } from "./document-retention-data-source";

export class HttpDocumentRetentionDataSource implements DocumentRetentionDataSource {
  constructor(private readonly http: HttpClient) {}
  async retirement(company: string, revisionId: string, signal: AbortSignal) {
    return documentRetirementReviewDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/documents/revisions/${revisionId}/retirement` },
        signal,
      ),
    );
  }
  async retire(
    company: string,
    operation: string,
    input: DocumentRetirementCommandDto,
    signal: AbortSignal,
  ) {
    return documentRetirementReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/documents/revisions/${input.revisionId}/retire`,
          method: "POST",
          operationId: operation,
          body: {
            expectedRevisionVersion: input.expectedRevisionVersion,
            expectedRetentionVersion: input.expectedRetentionVersion,
            reason: input.reason,
          },
        },
        signal,
      ),
    );
  }
  async state(company: string, documentId: string, signal: AbortSignal) {
    return documentRetentionDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/documents/${documentId}/retention` },
        signal,
      ),
    );
  }
  async history(company: string, documentId: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "20" });
    if (after !== null) query.set("after", after);
    return documentRetentionHistoryDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/documents/${documentId}/retention/history?${query}` },
        signal,
      ),
    );
  }
  async change(
    company: string,
    operation: string,
    input: DocumentRetentionCommandDto,
    signal: AbortSignal,
  ) {
    return documentRetentionReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/documents/${input.documentId}/retention/${input.intent}`,
          method: "POST",
          operationId: operation,
          body: { expectedVersion: input.expectedVersion, reason: input.reason },
        },
        signal,
      ),
    );
  }
  async policies(company: string, signal: AbortSignal) {
    return documentRetentionPoliciesDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/documents/retention-policies` },
        signal,
      ),
    );
  }
  async policyHistory(company: string, id: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "20" });
    if (after !== null) query.set("after", after);
    return documentRetentionPolicyPageDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/documents/retention-policies/${id}/history?${query}`,
        },
        signal,
      ),
    );
  }
  async savePolicy(
    company: string,
    operation: string,
    input: DocumentRetentionPolicyChangeDto,
    signal: AbortSignal,
  ) {
    return documentRetentionReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/documents/retention-policies`,
          method: "POST",
          operationId: operation,
          body: input,
        },
        signal,
      ),
    );
  }
}
