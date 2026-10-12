import type { CompanyId } from "../../../../core/domain/identifiers";
import type { DocumentRevisionAction } from "./document-revision-action";
import type { DocumentUploadContinuation } from "./document-upload-continuation";

export type DocumentRevision = Readonly<{
  id: string;
  companyId: CompanyId;
  documentId: string;
  number: number;
  fileName: string;
  mediaType: "application/pdf" | "image/jpeg" | "image/png";
  size: number;
  sha256: string;
  status:
    | "UPLOADING"
    | "VALIDATING"
    | "VALIDATION_FAILED"
    | "READY"
    | "REJECTED"
    | "CANCELLED"
    | "EXPIRED"
    | "RETIRED";
  uploadedBytes: number;
  version: number;
  createdBy: string;
  createdAt: string;
  expiresAt: string;
  reason: string;
  validationJobId: string | null;
  validationAttempts: number;
  failureCode: string | null;
  validatedAt: string | null;
  availableActions: readonly DocumentRevisionAction[];
  uploadContinuation: DocumentUploadContinuation | null;
}>;
export type DocumentRevisionPage = Readonly<{
  items: readonly DocumentRevision[];
  nextCursor: string | null;
}>;
