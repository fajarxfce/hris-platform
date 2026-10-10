export type LeaveAttachment = Readonly<{
  documentId: string;
  revisionId: string;
  fileName: string;
  mediaType: string;
  size: number;
  sha256: string;
}>;
