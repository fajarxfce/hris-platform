import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Document, DocumentPage } from "../../domain/entities/document";
import type { DocumentDto, DocumentPageDto } from "../models/document-dto";

export function toDocument(raw: DocumentDto, company: CompanyId): Document {
  if (
    raw.metadataSupported &&
    (!raw.metadata || raw.metadataVersion === null || raw.metadataVersion > raw.version)
  )
    throw new InvalidHttpResponseError();
  if (!raw.metadataSupported && (raw.metadata !== null || raw.metadataVersion !== null))
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    companyId: company,
    metadata: raw.metadata ? Object.freeze({ ...raw.metadata }) : null,
    id: raw.id.toLowerCase(),
    employmentId: raw.employmentId.toLowerCase(),
    createdBy: raw.createdBy.toLowerCase(),
    currentRevisionId: raw.currentRevisionId?.toLowerCase() ?? null,
  });
}
export function toDocumentPage(
  raw: DocumentPageDto,
  company: CompanyId,
  employmentId: string,
  after: string | null,
): DocumentPage {
  const items = raw.items.map((item) => toDocument(item, company));
  let previous = after ?? "";
  for (const item of items) {
    if (item.employmentId !== employmentId || item.id <= previous)
      throw new InvalidHttpResponseError();
    previous = item.id;
  }
  const cursor = raw.nextCursor?.toLowerCase() ?? null;
  if (cursor !== null && (items.length !== 20 || cursor !== items.at(-1)?.id || cursor === after))
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: cursor });
}
