import type { BinaryFileContent } from "../../../../core/domain/files/binary-file";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Result } from "../../../../core/domain/result";
import type { Document, DocumentPage } from "../entities/document";
import type {
  DocumentEmployeeDirectory,
  DocumentEmployeeSearch,
} from "../entities/document-employee";
import type { DocumentRevision, DocumentRevisionPage } from "../entities/document-revision";
import type { DocumentValidationAttempt } from "../entities/document-validation-attempt";

export interface DocumentRepository {
  employees(
    company: CompanyId,
    search: DocumentEmployeeSearch,
    employmentId: string | null,
    signal: AbortSignal,
  ): Promise<Result<DocumentEmployeeDirectory>>;
  list(
    company: CompanyId,
    employmentId: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<DocumentPage>>;
  get(company: CompanyId, id: string, signal: AbortSignal): Promise<Result<Document>>;
  revisions(
    company: CompanyId,
    documentId: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<DocumentRevisionPage>>;
  revision(company: CompanyId, id: string, signal: AbortSignal): Promise<Result<DocumentRevision>>;
  validationAttempts(
    company: CompanyId,
    revisionId: string,
    signal: AbortSignal,
  ): Promise<Result<readonly DocumentValidationAttempt[]>>;
  /** Success transfers buffer ownership to the caller, who releases them after handoff. */
  content(
    company: CompanyId,
    revision: DocumentRevision,
    signal: AbortSignal,
  ): Promise<Result<BinaryFileContent>>;
}
