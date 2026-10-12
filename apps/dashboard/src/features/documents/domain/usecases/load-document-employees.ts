import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentEmployeeSearch } from "../entities/document-employee";
import { canBrowseDocuments, validDocumentEmployeeSearch } from "../policies/document-read-policy";
import type { DocumentRepository } from "../repositories/document-repository";

export class LoadDocumentEmployees {
  constructor(private readonly documents: DocumentRepository) {}
  execute(access: CompanyAccess, search: DocumentEmployeeSearch, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canBrowseDocuments(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!validDocumentEmployeeSearch(search)) return Promise.resolve(failed("invalid_page"));
    return this.documents.employees(
      access.companyId,
      Object.freeze({ ...search, query: search.query.trim() }),
      null,
      signal,
    );
  }
}
