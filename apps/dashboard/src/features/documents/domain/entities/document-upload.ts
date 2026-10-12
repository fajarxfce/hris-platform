import type { BinaryFileReference } from "../../../../core/domain/files/binary-file-input";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { Document } from "./document";
import type { DocumentEmployee } from "./document-employee";
import type { DocumentRevision } from "./document-revision";

export type DocumentUploadTarget =
  | Readonly<{ kind: "new"; employmentId: string }>
  | Readonly<{ kind: "replacement"; documentId: string }>
  | Readonly<{ kind: "resume"; documentId: string; revisionId: string }>;

export type DocumentUploadReview =
  | Readonly<{ kind: "new"; employee: DocumentEmployee }>
  | Readonly<{ kind: "replacement"; employee: DocumentEmployee; document: Document }>
  | Readonly<{
      kind: "resume";
      employee: DocumentEmployee;
      document: Document;
      revision: DocumentRevision;
    }>;

export type DocumentUploadFile = Readonly<{
  reference: BinaryFileReference;
  mediaType: DocumentRevision["mediaType"];
  sha256: string;
}>;
export type DocumentUploadFields = Readonly<{
  title: string;
  classification: Document["classification"];
  reason: string;
}>;
export type DocumentUploadIdentifiers = Readonly<{
  documentId: string;
  revisionId: string;
  startOperation: string | null;
  chunkOperations: readonly string[];
}>;
export type DocumentUploadStart = Readonly<{
  documentId: string;
  revisionId: string;
  employmentId: string;
  title: string;
  classification: Document["classification"];
  expectedDocumentVersion: number;
  fileName: string;
  mediaType: DocumentRevision["mediaType"];
  size: number;
  sha256: string;
  reason: string;
}>;

/** Immutable command references survive explicit retry; file bytes remain owned by data. */
export type DocumentUploadPlan = Readonly<{
  companyId: CompanyId;
  documentId: string;
  revisionId: string;
  file: DocumentUploadFile;
  start: Readonly<{ operation: OperationId; input: DocumentUploadStart }> | null;
  chunkOperations: readonly OperationId[];
}>;
export type DocumentUploadChunk = Readonly<{
  revisionId: string;
  version: number;
  offset: number;
  sha256: string;
  bytes: ArrayBuffer;
}>;
