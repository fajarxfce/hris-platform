import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  DocumentRevision,
  DocumentRevisionPage,
} from "../../domain/entities/document-revision";
import {
  type DocumentRevisionAction,
  documentRevisionActions,
} from "../../domain/entities/document-revision-action";
import type { DocumentValidationAttempt } from "../../domain/entities/document-validation-attempt";
import type {
  DocumentRevisionDto,
  DocumentRevisionPageDto,
  DocumentValidationAttemptDto,
} from "../models/document-dto";

export function toDocumentRevision(raw: DocumentRevisionDto, company: CompanyId): DocumentRevision {
  const continuation = raw.uploadContinuation;
  if (
    new Set(raw.availableActions).size !== raw.availableActions.length ||
    raw.uploadedBytes > raw.size ||
    (raw.uploadedBytes !== raw.size && raw.uploadedBytes % raw.chunkSize !== 0) ||
    (raw.status === "READY" && (raw.uploadedBytes !== raw.size || raw.validatedAt === null)) ||
    (raw.availableActions.includes("UPLOAD_CONTENT") &&
      (raw.status !== "UPLOADING" || raw.uploadedBytes >= raw.size)) ||
    (continuation !== null &&
      (!raw.availableActions.includes("UPLOAD_CONTENT") ||
        continuation.offset !== raw.uploadedBytes ||
        continuation.size !== Math.min(raw.chunkSize, raw.size - raw.uploadedBytes)))
  )
    throw new InvalidHttpResponseError();
  const extension = raw.fileName.toLowerCase().split(".").at(-1);
  const extensions =
    raw.mediaType === "application/pdf"
      ? ["pdf"]
      : raw.mediaType === "image/png"
        ? ["png"]
        : ["jpg", "jpeg"];
  if (!extension || !extensions.includes(extension)) throw new InvalidHttpResponseError();
  const { chunkSize: _chunkSize, ...metadata } = raw;
  return Object.freeze({
    ...metadata,
    companyId: company,
    id: raw.id.toLowerCase(),
    documentId: raw.documentId.toLowerCase(),
    createdBy: raw.createdBy.toLowerCase(),
    validationJobId: raw.validationJobId?.toLowerCase() ?? null,
    uploadContinuation:
      continuation === null
        ? null
        : Object.freeze({
            ...continuation,
            operationId: continuation.operationId.toLowerCase(),
          }),
    // New server capabilities must not enable an unknown action or hide otherwise readable metadata.
    availableActions: Object.freeze(
      raw.availableActions.filter((value): value is DocumentRevisionAction =>
        documentRevisionActions.includes(value as DocumentRevisionAction),
      ),
    ),
  });
}
export function toDocumentRevisions(
  raw: DocumentRevisionPageDto,
  company: CompanyId,
  documentId: string,
  after: string | null,
): DocumentRevisionPage {
  const items = raw.items.map((item) => toDocumentRevision(item, company));
  let previous = after === null ? 101 : Number(after);
  for (const item of items) {
    if (item.documentId !== documentId || item.number >= previous)
      throw new InvalidHttpResponseError();
    previous = item.number;
  }
  const cursor = raw.nextCursor;
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    (cursor !== null &&
      (items.length !== 20 ||
        cursor !== String(items.at(-1)?.number) ||
        Number(cursor) < 2 ||
        cursor === after))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: cursor });
}
export function toDocumentAttempts(
  raw: DocumentValidationAttemptDto[],
): readonly DocumentValidationAttempt[] {
  if (
    new Set(raw.map((item) => item.jobId.toLowerCase())).size !== raw.length ||
    raw.some((item, index) => item.number !== index + 1)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze(
    raw.map((item) =>
      Object.freeze({
        ...item,
        jobId: item.jobId.toLowerCase(),
        actorId: item.actorId.toLowerCase(),
      }),
    ),
  );
}
