import type { FileRepository } from "../../../../core/domain/files/file-repository";
import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canBrowseDocuments } from "../policies/document-read-policy";
import type { DocumentRepository } from "../repositories/document-repository";

export class DownloadDocumentRevision {
  constructor(
    private readonly documents: DocumentRepository,
    private readonly files: Pick<FileRepository, "downloadBinary">,
  ) {}
  async execute(
    access: CompanyAccess,
    documentId: string,
    revisionId: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canBrowseDocuments(access.permissions)) return failed("access_denied");
    if (!isUuid(documentId) || !isUuid(revisionId)) return failed("document_revision_not_found");
    const found = await this.documents.revision(access.companyId, revisionId.toLowerCase(), signal);
    signal.throwIfAborted();
    if (!found.ok) return found;
    const revision = found.value;
    if (revision.documentId !== documentId.toLowerCase())
      return failed("document_revision_not_found");
    if (revision.status !== "READY") return failed("document_not_ready");
    const content = await this.documents.content(access.companyId, revision, signal);
    if (!content.ok) {
      signal.throwIfAborted();
      return content;
    }
    try {
      signal.throwIfAborted();
      return await this.files.downloadBinary(
        Object.freeze({ ...content.value, name: revision.fileName, mediaType: revision.mediaType }),
        signal,
      );
    } finally {
      for (const part of content.value.parts) new Uint8Array(part).fill(0);
    }
  }
}
