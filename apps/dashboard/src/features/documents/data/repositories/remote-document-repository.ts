import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import { binaryFilePartBytes } from "../../../../core/domain/files/binary-file";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { DocumentEmployeeSearch } from "../../domain/entities/document-employee";
import type { DocumentRevision } from "../../domain/entities/document-revision";
import type { DocumentRevisionChange } from "../../domain/entities/document-revision-action";
import type {
  DocumentUploadChunk,
  DocumentUploadStart,
} from "../../domain/entities/document-upload";
import type { DocumentRepository } from "../../domain/repositories/document-repository";
import type { DocumentDataSource } from "../datasources/document-data-source";
import { toDocumentEmployees } from "../mappers/document-employee-mapper";
import { toDocument, toDocumentPage } from "../mappers/document-mapper";
import { toDocumentMutation, toDocumentReceipt } from "../mappers/document-mutation-mapper";
import {
  toDocumentAttempts,
  toDocumentRevision,
  toDocumentRevisions,
} from "../mappers/document-revision-mapper";

export class RemoteDocumentRepository implements DocumentRepository {
  constructor(private readonly source: DocumentDataSource) {}
  startUpload(
    company: CompanyId,
    operation: OperationId,
    input: DocumentUploadStart,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toDocumentReceipt(
        await this.source.startUpload(company, operation, { ...input }, signal),
        input.revisionId,
        0,
      ),
    );
  }
  uploadChunk(
    company: CompanyId,
    operation: OperationId,
    part: DocumentUploadChunk,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toDocumentReceipt(
        await this.source.uploadChunk(
          company,
          part.revisionId,
          operation,
          part.offset,
          part.bytes,
          part.sha256,
          signal,
        ),
        part.revisionId,
        part.version + 1,
      ),
    );
  }
  validate(
    company: CompanyId,
    operation: OperationId,
    change: DocumentRevisionChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toDocumentMutation(
        await this.source.validate(
          company,
          change.id,
          operation,
          { expectedVersion: change.version, reason: change.reason },
          signal,
        ),
        change,
      ),
    );
  }
  cancelUpload(
    company: CompanyId,
    operation: OperationId,
    change: DocumentRevisionChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toDocumentMutation(
        await this.source.cancelUpload(
          company,
          change.id,
          operation,
          { expectedVersion: change.version, reason: change.reason },
          signal,
        ),
        change,
      ),
    );
  }
  employees(
    company: CompanyId,
    search: DocumentEmployeeSearch,
    employmentId: string | null,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toDocumentEmployees(
        await this.source.employees(company, search.query, search.after, employmentId, signal),
        company,
        search,
        employmentId,
      ),
    );
  }
  list(company: CompanyId, employmentId: string, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toDocumentPage(
        await this.source.list(company, employmentId, after, signal),
        company,
        employmentId,
        after,
      ),
    );
  }
  get(company: CompanyId, id: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const document = toDocument(await this.source.get(company, id, signal), company);
      if (document.id !== id) throw new InvalidHttpResponseError();
      return document;
    });
  }
  revisions(company: CompanyId, documentId: string, after: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toDocumentRevisions(
        await this.source.revisions(company, documentId, after, signal),
        company,
        documentId,
        after,
      ),
    );
  }
  revision(company: CompanyId, id: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const revision = toDocumentRevision(await this.source.revision(company, id, signal), company);
      if (revision.id !== id) throw new InvalidHttpResponseError();
      return revision;
    });
  }
  validationAttempts(company: CompanyId, revisionId: string, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toDocumentAttempts(await this.source.validationAttempts(company, revisionId, signal)),
    );
  }
  async content(company: CompanyId, revision: DocumentRevision, signal: AbortSignal) {
    const acquiredParts: ArrayBuffer[] = [];
    try {
      const result = await safeHttpCall(signal, async () => {
        const content = await this.source.content(company, revision, signal);
        acquiredParts.push(...content.parts);
        if (
          content.byteLength !== revision.size ||
          content.mediaType !== revision.mediaType ||
          content.etag !== `"${revision.id}-${revision.sha256}"` ||
          content.parts.some(
            (part) => part.byteLength < 1 || part.byteLength > binaryFilePartBytes,
          ) ||
          content.parts.reduce((size, part) => size + part.byteLength, 0) !== revision.size
        )
          throw new InvalidHttpResponseError();
        return Object.freeze({
          byteLength: content.byteLength,
          parts: Object.freeze([...content.parts]),
        });
      });
      // Transfer the owned buffers only after mapping and cancellation checks succeed.
      if (result.ok) acquiredParts.length = 0;
      return result;
    } finally {
      for (const part of acquiredParts) new Uint8Array(part).fill(0);
    }
  }
}
