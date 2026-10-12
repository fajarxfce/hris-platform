import type { BinaryResponseDto } from "../../../../core/data/http/binary-response-dto";
import type {
  DocumentDto,
  DocumentEmployeeDirectoryDto,
  DocumentPageDto,
  DocumentRevisionDto,
  DocumentRevisionPageDto,
  DocumentValidationAttemptDto,
} from "../models/document-dto";

export interface DocumentDataSource {
  validate(
    company: string,
    id: string,
    operation: string,
    body: DocumentMutationDto,
    signal: AbortSignal,
  ): Promise<DocumentMutationReceiptDto>;
  cancelUpload(
    company: string,
    id: string,
    operation: string,
    body: DocumentMutationDto,
    signal: AbortSignal,
  ): Promise<DocumentMutationReceiptDto>;
  employees(
    company: string,
    query: string,
    after: string | null,
    employmentId: string | null,
    signal: AbortSignal,
  ): Promise<DocumentEmployeeDirectoryDto>;
  list(
    company: string,
    employmentId: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<DocumentPageDto>;
  get(company: string, id: string, signal: AbortSignal): Promise<DocumentDto>;
  revisions(
    company: string,
    documentId: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<DocumentRevisionPageDto>;
  revision(company: string, id: string, signal: AbortSignal): Promise<DocumentRevisionDto>;
  validationAttempts(
    company: string,
    revisionId: string,
    signal: AbortSignal,
  ): Promise<DocumentValidationAttemptDto[]>;
  content(
    company: string,
    revision: Pick<DocumentRevisionDto, "id" | "mediaType" | "size" | "sha256">,
    signal: AbortSignal,
  ): Promise<BinaryResponseDto>;
}

import type {
  DocumentMutationDto,
  DocumentMutationReceiptDto,
} from "../models/document-mutation-dto";
