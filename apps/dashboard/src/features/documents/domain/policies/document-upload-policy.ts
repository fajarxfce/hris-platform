import type { BinaryFileReference } from "../../../../core/domain/files/binary-file-input";
import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentRevision } from "../entities/document-revision";
import type {
  DocumentUploadFields,
  DocumentUploadFile,
  DocumentUploadIdentifiers,
  DocumentUploadPlan,
  DocumentUploadReview,
} from "../entities/document-upload";
import { hasDocumentManagement } from "./document-action-policy";
import { prepareDocumentBusinessMetadata } from "./document-business-metadata-policy";
import { canBrowseDocuments } from "./document-read-policy";

export const documentUploadChunkBytes = 1024 * 1024;
export const documentUploadMaximumBytes = 100 * documentUploadChunkBytes;
export const documentUploadPartCount = (size: number): number =>
  Math.ceil(size / documentUploadChunkBytes);
export const canCreateCompanyDocument = (permissions: readonly string[]): boolean =>
  canBrowseDocuments(permissions) &&
  permissions.includes("documents.manage") &&
  permissions.includes("people.profile.manage");

export const canResumeDocument = (access: CompanyAccess, revision: DocumentRevision): boolean =>
  canBrowseDocuments(access.permissions) &&
  hasDocumentManagement(access.permissions) &&
  revision.companyId === access.companyId &&
  revision.availableActions.includes("UPLOAD_CONTENT");

export function documentFileMediaType(
  file: BinaryFileReference,
): Result<DocumentRevision["mediaType"]> {
  if (
    !file.id ||
    file.id.length > 128 ||
    !file.name.trim() ||
    file.name.length > 180 ||
    /[\p{Cc}/\\]/u.test(file.name) ||
    !Number.isSafeInteger(file.byteLength) ||
    file.byteLength < 1 ||
    file.byteLength > documentUploadMaximumBytes
  )
    return failed("invalid_document_file");
  switch (file.name.toLowerCase().split(".").at(-1)) {
    case "pdf":
      return success("application/pdf");
    case "png":
      return success("image/png");
    case "jpg":
    case "jpeg":
      return success("image/jpeg");
    default:
      return failed("invalid_document_file");
  }
}

export function prepareDocumentUpload(
  access: CompanyAccess,
  review: DocumentUploadReview,
  file: DocumentUploadFile,
  fields: DocumentUploadFields,
  identifiers: DocumentUploadIdentifiers,
): Result<DocumentUploadPlan> {
  if (
    review.employee.companyId !== access.companyId ||
    (review.kind === "resume"
      ? !canResumeDocument(access, review.revision)
      : !canCreateCompanyDocument(access.permissions))
  )
    return failed("access_denied");
  const media = documentFileMediaType(file.reference);
  if (!media.ok) return media;
  if (media.value !== file.mediaType || !/^[0-9a-f]{64}$/u.test(file.sha256))
    return failed("invalid_document_file");
  if (
    review.kind === "resume" &&
    (review.revision.sha256 !== file.sha256 ||
      review.revision.size !== file.reference.byteLength ||
      review.revision.mediaType !== file.mediaType)
  )
    return failed("document_file_mismatch");
  const title = review.kind === "new" ? fields.title.trim() : review.document.title;
  const classification =
    review.kind === "new" ? fields.classification : review.document.classification;
  const reason = review.kind === "resume" ? review.revision.reason : fields.reason.trim();
  const version = review.kind === "new" ? 0 : review.document.version;
  const documentId = identifiers.documentId.toLowerCase();
  const revisionId = identifiers.revisionId.toLowerCase();
  const operation = identifiers.startOperation?.toLowerCase() ?? null;
  const chunkOperations = identifiers.chunkOperations.map((id) => id.toLowerCase());
  const assigned = [
    documentId,
    revisionId,
    ...(operation === null ? [] : [operation]),
    ...chunkOperations,
  ];
  const partCount = documentUploadPartCount(file.reference.byteLength);
  if (
    !title ||
    title.length > 160 ||
    !reason ||
    reason.length > 1000 ||
    !["PERSONAL", "RECEIPT", "HR_ONLY"].includes(classification) ||
    !Number.isSafeInteger(version) ||
    version < 0 ||
    version >= Number.MAX_SAFE_INTEGER ||
    chunkOperations.length !== partCount ||
    assigned.some((id) => !isUuid(id)) ||
    new Set(assigned).size !== assigned.length ||
    (review.kind === "resume" ? operation !== null : operation === null)
  )
    return failed("invalid_document_upload");
  if (
    !documentId ||
    !revisionId ||
    !isUuid(documentId) ||
    !isUuid(revisionId) ||
    documentId === revisionId ||
    !isUuid(review.employee.id) ||
    (review.kind !== "new" &&
      (review.document.companyId !== access.companyId ||
        review.document.id !== documentId ||
        review.document.employmentId !== review.employee.id ||
        (review.kind === "resume" &&
          (review.revision.documentId !== documentId || review.revision.id !== revisionId))))
  )
    return failed("invalid_document_upload");
  if (fields.metadata && (review.kind !== "new" || !review.metadataSupported))
    return failed("document_metadata_unsupported");
  const metadata = fields.metadata ? prepareDocumentBusinessMetadata(fields.metadata) : null;
  if (metadata && !metadata.ok) return metadata;
  const ownedFile = Object.freeze({ ...file, reference: Object.freeze({ ...file.reference }) });
  return success(
    Object.freeze({
      companyId: access.companyId,
      documentId,
      revisionId,
      file: ownedFile,
      chunkOperations: Object.freeze(chunkOperations as OperationId[]),
      start:
        operation === null
          ? null
          : Object.freeze({
              operation: operation as OperationId,
              input: Object.freeze({
                documentId,
                revisionId,
                employmentId: review.employee.id,
                title,
                classification,
                expectedDocumentVersion: version,
                fileName: file.reference.name,
                mediaType: file.mediaType,
                size: file.reference.byteLength,
                sha256: file.sha256,
                reason,
                ...(metadata?.ok ? { metadata: metadata.value } : {}),
              }),
            }),
    }),
  );
}
