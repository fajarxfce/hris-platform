import type { FileRepository } from "../../../../core/domain/files/file-repository";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canImportEmployees } from "../policies/employee-import-policy";
import type { EmployeeImportRepository } from "../repositories/employee-import-repository";

export class DownloadEmployeeImportTemplate {
  constructor(
    private readonly imports: EmployeeImportRepository,
    private readonly files: FileRepository,
  ) {}
  async execute(access: CompanyAccess, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canImportEmployees(access.permissions)) return failed("employee_import_access_required");
    const template = await this.imports.template(access.companyId, signal);
    signal.throwIfAborted();
    if (!template.ok) return template;
    return this.files.downloadText(
      { name: "employee-import-template.csv", mediaType: "text/csv", text: template.value },
      signal,
    );
  }
}
