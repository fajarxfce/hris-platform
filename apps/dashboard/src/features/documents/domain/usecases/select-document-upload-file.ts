import type { FileInputRepository } from "../../../../core/domain/files/file-input-repository";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentUploadFile, DocumentUploadReview } from "../entities/document-upload";
import {
  canCreateCompanyDocument,
  canResumeDocument,
  documentFileMediaType,
  documentUploadMaximumBytes,
} from "../policies/document-upload-policy";

export class SelectDocumentUploadFile {
  constructor(private readonly files: FileInputRepository) {}
  async execute(
    access: CompanyAccess,
    review: DocumentUploadReview,
    signal: AbortSignal,
  ): Promise<Result<DocumentUploadFile | null>> {
    signal.throwIfAborted();
    if (
      review.employee.companyId !== access.companyId ||
      (review.kind === "resume"
        ? !canResumeDocument(access, review.revision)
        : !canCreateCompanyDocument(access.permissions))
    )
      return failed("access_denied");
    let owned: string | null = null;
    try {
      const selected = await this.files.select(
        { accept: ".pdf,.png,.jpg,.jpeg", maximumBytes: documentUploadMaximumBytes },
        signal,
      );
      if (selected.ok && selected.value !== null) owned = selected.value.id;
      signal.throwIfAborted();
      if (!selected.ok) return selected;
      if (selected.value === null) return success(null);
      const media = documentFileMediaType(selected.value);
      if (!media.ok) return media;
      const hash = await this.files.hash(selected.value.id, signal);
      signal.throwIfAborted();
      if (!hash.ok) return hash;
      if (
        review.kind === "resume" &&
        (review.revision.size !== selected.value.byteLength ||
          review.revision.sha256 !== hash.value ||
          review.revision.mediaType !== media.value)
      )
        return failed("document_file_mismatch");
      const file = Object.freeze({
        reference: Object.freeze({ ...selected.value }),
        mediaType: media.value,
        sha256: hash.value,
      });
      owned = null;
      return success(file);
    } finally {
      if (owned !== null) this.files.release(owned);
    }
  }
}
