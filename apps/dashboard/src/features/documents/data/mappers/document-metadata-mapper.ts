import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { DocumentMetadataPage } from "../../domain/entities/document-metadata";
import type { DocumentMetadataPageDto } from "../models/document-metadata-dto";

export function toDocumentMetadataPage(
  raw: DocumentMetadataPageDto,
  companyId: CompanyId,
  documentId: string,
  after: string | null,
): DocumentMetadataPage {
  let previous = after === null ? -1 : Number(after);
  let documentVersion = -1;
  const items = raw.items.map((row) => {
    if (
      row.documentId.toLowerCase() !== documentId ||
      row.version !== previous + 1 ||
      row.documentVersion <= documentVersion
    )
      throw new InvalidHttpResponseError();
    previous = row.version;
    documentVersion = row.documentVersion;
    return Object.freeze({
      ...row,
      companyId,
      documentId,
      actorId: row.actorId?.toLowerCase() ?? null,
      metadata: Object.freeze({ ...row.metadata }),
    });
  });
  if (
    raw.nextCursor !== null &&
    (items.length !== 20 || raw.nextCursor !== String(previous) || previous >= 999)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: raw.nextCursor });
}
