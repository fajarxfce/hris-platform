import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentFolder } from "../entities/document-folder";
import { canBrowseDocuments } from "../policies/document-read-policy";
import type { DocumentRepository } from "../repositories/document-repository";

export class LoadEmployeeDocuments {
  constructor(private readonly documents: DocumentRepository) {}
  async execute(
    access: CompanyAccess,
    employeeId: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<DocumentFolder>> {
    signal.throwIfAborted();
    if (!canBrowseDocuments(access.permissions)) return failed("access_denied");
    if (!isUuid(employeeId)) return failed("employee_not_found");
    if (after !== null && !isUuid(after)) return failed("invalid_document_page");
    const id = employeeId.toLowerCase();
    const found = await this.documents.employees(
      access.companyId,
      { query: "", after: null },
      id,
      signal,
    );
    signal.throwIfAborted();
    if (!found.ok) return found;
    const employee = found.value.employees.items.find((item) => item.id === id);
    if (!employee) return failed("employee_not_found");
    const page = await this.documents.list(
      access.companyId,
      id,
      after?.toLowerCase() ?? null,
      signal,
    );
    signal.throwIfAborted();
    return page.ok ? { ok: true, value: Object.freeze({ employee, documents: page.value }) } : page;
  }
}
