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
import type { DocumentDataSource } from "./document-data-source";

export class HttpDocumentDataSource implements DocumentDataSource {
  constructor(private readonly http: HttpClient) {}
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
