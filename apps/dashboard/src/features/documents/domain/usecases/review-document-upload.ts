import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentUploadReview, DocumentUploadTarget } from "../entities/document-upload";
import { hasDocumentManagement } from "../policies/document-action-policy";
import { canBrowseDocuments } from "../policies/document-read-policy";
import { canCreateCompanyDocument, canResumeDocument } from "../policies/document-upload-policy";
import type { DocumentRepository } from "../repositories/document-repository";

export class ReviewDocumentUpload {
  constructor(private readonly documents: DocumentRepository) {}
  async execute(
    access: CompanyAccess,
    target: DocumentUploadTarget,
    signal: AbortSignal,
  ): Promise<Result<DocumentUploadReview>> {
    signal.throwIfAborted();
    if (
      !canBrowseDocuments(access.permissions) ||
      !hasDocumentManagement(access.permissions) ||
      (target.kind !== "resume" && !canCreateCompanyDocument(access.permissions))
    )
      return failed("access_denied");
    if (
      target.kind === "new"
        ? !isUuid(target.employmentId)
        : !isUuid(target.documentId) || (target.kind === "resume" && !isUuid(target.revisionId))
    )
      return failed("document_not_found");
    const document =
      target.kind === "new"
        ? null
        : await this.documents.get(access.companyId, target.documentId.toLowerCase(), signal);
    signal.throwIfAborted();
    if (document !== null && !document.ok) return document;
    const employment =
      target.kind === "new" ? target.employmentId.toLowerCase() : document?.value.employmentId;
    if (!employment) return failed("employee_not_found");
    const found = await this.documents.employees(
      access.companyId,
      { query: "", after: null },
      employment,
      signal,
    );
    signal.throwIfAborted();
    if (!found.ok) return found;
    const employee = found.value.employees.items.find((item) => item.id === employment);
    if (!employee) return failed("employee_not_found");
    if (target.kind === "new")
      return success(
        Object.freeze({
          kind: "new",
          employee,
          metadataSupported: found.value.metadataSupported === true,
        }),
      );
    if (!document?.ok) return failed("document_not_found");
    if (target.kind === "replacement")
      return document.value.revisionCount >= 100
        ? failed("document_revision_limit")
        : success(Object.freeze({ kind: "replacement", employee, document: document.value }));
    const revision = await this.documents.revision(
      access.companyId,
      target.revisionId.toLowerCase(),
      signal,
    );
    signal.throwIfAborted();
    if (!revision.ok) return revision;
    if (revision.value.documentId !== document.value.id)
      return failed("document_revision_not_found");
    return canResumeDocument(access, revision.value)
      ? success(
          Object.freeze({
            kind: "resume",
            employee,
            document: document.value,
            revision: revision.value,
          }),
        )
      : failed("document_action_unavailable");
  }
}
