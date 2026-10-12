/** The current operator's original request for a reserved, uncommitted file part. */
export type DocumentUploadContinuation = Readonly<{
  operationId: string;
  offset: number;
  size: number;
  sha256: string;
  attempts: number;
  retryAt: string | null;
}>;
