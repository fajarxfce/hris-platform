import { binaryResponseDto } from "../../../../core/data/http/binary-response-dto";
import type { HttpClient } from "../../../../core/data/http/http-client";
import {
  type DocumentRevisionDto,
  documentDto,
  documentEmployeeDirectoryDto,
  documentPageDto,
  documentRevisionDto,
  documentRevisionPageDto,
  documentValidationAttemptsDto,
} from "../models/document-dto";
import {
  type DocumentMutationDto,
  documentMutationReceiptDto,
} from "../models/document-mutation-dto";
import {
  type DocumentUploadStartDto,
  documentUploadReceiptDto,
} from "../models/document-upload-dto";
import type { DocumentDataSource } from "./document-data-source";

export class HttpDocumentDataSource implements DocumentDataSource {
  constructor(private readonly http: HttpClient) {}
  async startUpload(
    company: string,
    operation: string,
    body: DocumentUploadStartDto,
    signal: AbortSignal,
  ) {
    return documentUploadReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/documents/uploads`,
          method: "POST",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
  async uploadChunk(
    company: string,
    id: string,
    operation: string,
    offset: number,
    bytes: ArrayBuffer,
    sha256: string,
    signal: AbortSignal,
  ) {
    return documentUploadReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/documents/revisions/${id}/chunks`,
          method: "POST",
          operationId: operation,
          timeoutMilliseconds: 60_000,
          binaryBody: {
            bytes,
            headers: { "Upload-Offset": String(offset), "Upload-Checksum-Sha256": sha256 },
          },
        },
        signal,
      ),
    );
  }
  async validate(
    company: string,
    id: string,
    operation: string,
    body: DocumentMutationDto,
    signal: AbortSignal,
  ) {
    return documentMutationReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/documents/revisions/${id}/validate`,
          method: "POST",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
  async cancelUpload(
    company: string,
    id: string,
    operation: string,
    body: DocumentMutationDto,
    signal: AbortSignal,
  ) {
    return documentMutationReceiptDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/documents/revisions/${id}/cancel`,
          method: "POST",
          operationId: operation,
          body,
        },
        signal,
      ),
    );
  }
  async employees(
    company: string,
    search: string,
    after: string | null,
    employmentId: string | null,
    signal: AbortSignal,
  ) {
    const query = new URLSearchParams({ query: search, limit: "20" });
    if (after !== null) query.set("after", after);
    if (employmentId !== null) query.set("employmentId", employmentId);
    return documentEmployeeDirectoryDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/documents/employees?${query}` },
        signal,
      ),
    );
  }
  async list(company: string, employmentId: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ employmentId, limit: "20" });
    if (after !== null) query.set("after", after);
    return documentPageDto.parse(
      await this.http.request({ path: `/api/v1/companies/${company}/documents?${query}` }, signal),
    );
  }
  async get(company: string, id: string, signal: AbortSignal) {
    return documentDto.parse(
      await this.http.request({ path: `/api/v1/companies/${company}/documents/${id}` }, signal),
    );
  }
  async revisions(company: string, documentId: string, after: string | null, signal: AbortSignal) {
    const query = new URLSearchParams({ limit: "20" });
    if (after !== null) query.set("after", after);
    return documentRevisionPageDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/documents/${documentId}/revisions?${query}` },
        signal,
      ),
    );
  }
  async revision(company: string, id: string, signal: AbortSignal) {
    return documentRevisionDto.parse(
      await this.http.request(
        { path: `/api/v1/companies/${company}/documents/revisions/${id}` },
        signal,
      ),
    );
  }
  async validationAttempts(company: string, revisionId: string, signal: AbortSignal) {
    return documentValidationAttemptsDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/documents/revisions/${revisionId}/validation-attempts`,
        },
        signal,
      ),
    );
  }
  async content(
    company: string,
    revision: Pick<DocumentRevisionDto, "id" | "mediaType" | "size" | "sha256">,
    signal: AbortSignal,
  ) {
    return binaryResponseDto.parse(
      await this.http.request(
        {
          path: `/api/v1/companies/${company}/documents/revisions/${revision.id}/content`,
          timeoutMilliseconds: 60_000,
          response: {
            type: "binary",
            mediaType: revision.mediaType,
            byteLength: revision.size,
            etag: `"${revision.id}-${revision.sha256}"`,
          },
        },
        signal,
      ),
    );
  }
}
