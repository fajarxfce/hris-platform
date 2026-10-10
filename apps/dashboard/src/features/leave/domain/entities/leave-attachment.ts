import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveRequestId } from "./leave-request";
export type LeaveAttachment = Readonly<{
  documentId: string;
  revisionId: string;
  fileName: string;
  mediaType: string;
  size: number;
  sha256: string;
}>;

export type LeaveAttachmentSource = Readonly<{
  id: LeaveRequestId;
  companyId: CompanyId;
  attachments: readonly LeaveAttachment[];
}>;
