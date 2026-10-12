import type { FileInputRepository } from "../../../../core/domain/files/file-input-repository";
import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentRevision } from "../entities/document-revision";
import type { DocumentUploadPlan } from "../entities/document-upload";
import { hasDocumentManagement } from "../policies/document-action-policy";
import { canBrowseDocuments } from "../policies/document-read-policy";
import {
  documentUploadChunkBytes,
  documentUploadPartCount,
} from "../policies/document-upload-policy";
import type { DocumentRepository } from "../repositories/document-repository";

export class UploadDocumentFile {
  constructor(
    private readonly documents: DocumentRepository,
    private readonly files: FileInputRepository,
  ) {}
  async execute(
    access: CompanyAccess,
    plan: DocumentUploadPlan,
    onProgress: (uploadedBytes: number) => void,
    signal: AbortSignal,
  ): Promise<Result<DocumentRevision>> {
    signal.throwIfAborted();
    if (
      plan.companyId !== access.companyId ||
      !canBrowseDocuments(access.permissions) ||
      !hasDocumentManagement(access.permissions)
    )
      return failed("access_denied");
    const size = plan.file.reference.byteLength;
    const partCount = documentUploadPartCount(size);
    if (
      !isUuid(plan.documentId) ||
      !isUuid(plan.revisionId) ||
      !Number.isSafeInteger(size) ||
      size < 1 ||
      partCount > 100 ||
      plan.chunkOperations.length !== partCount ||
      plan.chunkOperations.some((operation) => !isUuid(operation))
    )
      return failed("invalid_document_upload");
    if (plan.start !== null) {
      const started = await this.documents.startUpload(
        access.companyId,
        plan.start.operation,
        plan.start.input,
        signal,
      );
      signal.throwIfAborted();
      if (!started.ok) return started;
      onProgress(0);
    }
    const current = await this.documents.revision(access.companyId, plan.revisionId, signal);
    signal.throwIfAborted();
    if (!current.ok) return current;
    const revision = current.value;
    if (
      revision.documentId !== plan.documentId ||
      revision.companyId !== plan.companyId ||
      revision.size !== size ||
      revision.sha256 !== plan.file.sha256 ||
      revision.mediaType !== plan.file.mediaType
    )
      return failed("document_file_mismatch");
    if (!["UPLOADING", "VALIDATING", "VALIDATION_FAILED", "READY"].includes(revision.status))
      return failed("document_upload_closed");
    if (revision.uploadedBytes < size && !revision.availableActions.includes("UPLOAD_CONTENT"))
      return failed("document_action_unavailable");
    let offset = revision.uploadedBytes,
      version = revision.version;
    onProgress(offset);
    // Every successful iteration advances by one bounded part. There is no automatic retry.
    for (
      let ordinal = Math.floor(offset / documentUploadChunkBytes);
      ordinal < partCount && offset < size;
      ordinal += 1
    ) {
      signal.throwIfAborted();
      const part = await this.files.readPart(
        plan.file.reference.id,
        offset,
        Math.min(documentUploadChunkBytes, size - offset),
        signal,
      );
      if (!part.ok) {
        signal.throwIfAborted();
        return part;
      }
      try {
        signal.throwIfAborted();
        if (part.value.bytes.byteLength !== Math.min(documentUploadChunkBytes, size - offset))
          return failed("invalid_document_file");
        const reserved = offset === revision.uploadedBytes ? revision.uploadContinuation : null;
        if (
          reserved !== null &&
          (reserved.offset !== offset ||
            reserved.size !== part.value.bytes.byteLength ||
            reserved.sha256 !== part.value.sha256)
        )
          return failed("document_file_mismatch");
        const operation =
          (reserved?.operationId as OperationId | undefined) ?? plan.chunkOperations[ordinal];
        if (operation === undefined) return failed("invalid_document_upload");
        const committed = await this.documents.uploadChunk(
          access.companyId,
          operation,
          {
            revisionId: plan.revisionId,
            offset,
            version,
            bytes: part.value.bytes,
            sha256: part.value.sha256,
          },
          signal,
        );
        signal.throwIfAborted();
        if (!committed.ok) return committed;
        offset += part.value.bytes.byteLength;
        version = committed.value.version;
        onProgress(offset);
      } finally {
        new Uint8Array(part.value.bytes).fill(0);
      }
    }
    if (offset !== size) return failed("document_upload_incomplete");
    const completed = await this.documents.revision(access.companyId, plan.revisionId, signal);
    signal.throwIfAborted();
    if (!completed.ok) return completed;
    if (
      completed.value.documentId !== plan.documentId ||
      completed.value.sha256 !== plan.file.sha256 ||
      completed.value.size !== size ||
      completed.value.uploadedBytes !== size
    )
      return failed("document_file_mismatch");
    if (!["UPLOADING", "VALIDATING", "VALIDATION_FAILED", "READY"].includes(completed.value.status))
      return failed("document_upload_closed");
    return success(completed.value);
  }
}
