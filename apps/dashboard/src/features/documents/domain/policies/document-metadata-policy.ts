import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { type Failure, failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { Document } from "../entities/document";
import type { DocumentMetadataChange, DocumentMetadataFields } from "../entities/document-metadata";
import { documentActionAccessLost, documentActionWasRejected } from "./document-action-policy";
import {
  prepareDocumentBusinessMetadata,
  sameDocumentMetadata,
} from "./document-business-metadata-policy";
import { canCreateCompanyDocument } from "./document-upload-policy";

export const validDocumentMetadataCursor = (value: string | null): boolean =>
  value === null || /^(0|[1-9]\d{0,2})$/u.test(value);
export function prepareDocumentMetadata(
  access: CompanyAccess,
  operation: OperationId,
  document: Document,
  fields: DocumentMetadataFields,
): Result<DocumentMetadataChange> {
  if (!canCreateCompanyDocument(access.permissions) || document.companyId !== access.companyId)
    return failed("access_denied");
  if (!document.metadataSupported || !document.metadata || document.metadataVersion == null)
    return failed("document_metadata_unsupported");
  if (document.metadataVersion >= 999) return failed("document_metadata_limit");
  const reason = fields.reason.trim();
  if (
    !isUuid(operation) ||
    !isUuid(document.id) ||
    !Number.isSafeInteger(document.version) ||
    document.version < 0 ||
    document.version >= Number.MAX_SAFE_INTEGER ||
    !reason ||
    reason.length > 1000 ||
    /\p{Cc}/u.test(reason)
  )
    return failed("invalid_document_metadata_change");
  const value = prepareDocumentBusinessMetadata(fields);
  if (!value.ok) return value;
  if (sameDocumentMetadata(value.value, document.metadata))
    return failed("document_metadata_unchanged");
  return success(
    Object.freeze({
      documentId: document.id,
      expectedVersion: document.version,
      metadata: value.value,
      reason,
    }),
  );
}
export const documentMetadataReviewChanged = (failure: Failure): boolean =>
  [
    "stale_version",
    "document_metadata_changed",
    "document_metadata_limit",
    "document_archived",
    "document_metadata_unchanged",
  ].includes(failure.code);
export const documentMetadataAccessLost = (failure: Failure): boolean =>
  documentActionAccessLost(failure) || failure.code === "document_metadata_unsupported";
export const documentMetadataWasRejected = (failure: Failure): boolean =>
  documentActionWasRejected(failure) ||
  documentMetadataReviewChanged(failure) ||
  [
    "invalid_document_metadata",
    "invalid_document_metadata_change",
    "document_metadata_unsupported",
  ].includes(failure.code);
