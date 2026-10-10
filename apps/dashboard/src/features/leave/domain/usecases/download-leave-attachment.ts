import { maximumBinaryFileBytes } from "../../../../core/domain/files/binary-file";
import type { FileRepository } from "../../../../core/domain/files/file-repository";
import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveAttachmentSource } from "../entities/leave-attachment";
import type { LeaveRequestRepository } from "../repositories/leave-request-repository";

export class DownloadLeaveAttachment {
  constructor(
    private readonly requests: LeaveRequestRepository,
    private readonly files: Pick<FileRepository, "downloadBinary">,
  ) {}
  async execute(
    access: CompanyAccess,
    request: LeaveAttachmentSource,
    revision: string,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (access.companyId !== request.companyId) return failed("access_denied");
    if (!isUuid(request.id) || !isUuid(revision)) return failed("leave_attachment_not_found");
    const attachment = request.attachments.find(
      (item) => item.revisionId.toLowerCase() === revision.toLowerCase(),
    );
    if (!attachment) return failed("leave_attachment_not_found");
    if (
      !Number.isSafeInteger(attachment.size) ||
      attachment.size < 1 ||
      attachment.size > maximumBinaryFileBytes ||
      !["application/pdf", "image/jpeg", "image/png"].includes(attachment.mediaType) ||
      !/^[0-9a-f]{64}$/u.test(attachment.sha256) ||
      attachment.fileName.trim().length === 0 ||
      attachment.fileName.length > 180 ||
      /[\p{Cc}/\\]/u.test(attachment.fileName)
    )
      return failed("leave_attachment_unavailable");
    const content = await this.requests.downloadAttachment(
      access.companyId,
      request.id,
      attachment,
      signal,
    );
    signal.throwIfAborted();
    if (!content.ok) return content;
    // Buffers remain private to this operation and are released after native handoff.
    return this.files.downloadBinary(
      Object.freeze({
        ...content.value,
        name: attachment.fileName,
        mediaType: attachment.mediaType,
      }),
      signal,
    );
  }
}
