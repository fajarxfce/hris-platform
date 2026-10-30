import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type DocumentMetadataChangeDto,
  documentMetadataPageDto,
} from "../models/document-metadata-dto";
import { documentMutationReceiptDto } from "../models/document-mutation-dto";
import type { DocumentMetadataDataSource } from "./document-metadata-data-source";

export class HttpDocumentMetadataDataSource implements DocumentMetadataDataSource {
  constructor(private readonly http: HttpClient) {}
  async history(company: string, documentId: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "20" });
    if (after !== null) query.set("after", after);
    return documentMetadataPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/documents/${documentId}/metadata-history?${query}` },
        signal,
      ),
    );
  }
  async save(
    company: string,
    documentId: string,
    operation: string,
    body: DocumentMetadataChangeDto,
    signal: AbortSignal,
  ) {
    return documentMutationReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/documents/${documentId}/metadata`,
          method: "PUT",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
}
