import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type {
  DocumentRetention,
  DocumentRetentionCommand,
  DocumentRetentionHistory,
} from "../../domain/entities/document-retention";
import type { DocumentRetentionReceiptDto } from "../models/document-retention-dto";
import type {
  DocumentRetentionDto,
  DocumentRetentionHistoryDto,
} from "../models/document-retention-state-dto";

export function toDocumentRetention(
  raw: DocumentRetentionDto,
  company: CompanyId,
  documentId: string,
): DocumentRetention {
  if (
    raw.documentId.toLowerCase() !== documentId ||
    (raw.version === null && (raw.archive !== null || raw.legalHold))
  )
    throw new InvalidHttpResponseError();
  const archive = raw.archive;
  if (
    archive &&
    ((archive.policyId === null) !== (archive.policyVersion === null) ||
      (archive.policyId === null && archive.retentionDays !== null) ||
      (archive.retentionDays === null) !== (archive.eligibleAt === null) ||
      (archive.eligibleAt !== null &&
        Date.parse(archive.eligibleAt) - Date.parse(archive.archivedAt) !==
          (archive.retentionDays ?? 0) * 86_400_000))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    companyId: company,
    documentId,
    archive: archive
      ? Object.freeze({ ...archive, policyId: archive.policyId?.toLowerCase() ?? null })
      : null,
  });
}
export function toDocumentRetentionHistory(
  raw: DocumentRetentionHistoryDto,
  company: CompanyId,
  documentId: string,
  after: string | null,
): DocumentRetentionHistory {
  let previous = after === null ? -1 : Number(after);
  const items = raw.items.map((entry) => {
    const state = toDocumentRetention(entry.state, company, documentId);
    if (
      state.version === null ||
      state.version <= previous ||
      !entry.reason.trim() ||
      (entry.action === "RETIRE") !== (entry.retiredRevisionId !== null) ||
      (entry.action === "ARCHIVE" && state.archive === null) ||
      (entry.action === "RESTORE" && state.archive !== null) ||
      (entry.action === "PLACE_HOLD" && !state.legalHold) ||
      (entry.action === "RELEASE_HOLD" && state.legalHold) ||
      (entry.action === "RETIRE" && (state.archive === null || state.legalHold))
    )
      throw new InvalidHttpResponseError();
    previous = state.version;
    return Object.freeze({
      ...entry,
      state,
      actorId: entry.actorId.toLowerCase(),
      retiredRevisionId: entry.retiredRevisionId?.toLowerCase() ?? null,
    });
  });
  if (
    raw.nextCursor !== null &&
    (items.length !== 20 ||
      Number(raw.nextCursor) !== items.at(-1)?.state.version ||
      raw.nextCursor === after)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor: raw.nextCursor });
}
export function toDocumentRetentionChangeReceipt(
  raw: DocumentRetentionReceiptDto,
  input: DocumentRetentionCommand,
) {
  if (
    raw.id.toLowerCase() !== input.documentId ||
    raw.version !== (input.expectedVersion ?? -1) + 1
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: raw.id.toLowerCase(), version: raw.version });
}
