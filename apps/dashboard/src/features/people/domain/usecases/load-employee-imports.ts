import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canImportEmployees, isEmployeeImportCursor } from "../policies/employee-import-policy";
import type { EmployeeImportRepository } from "../repositories/employee-import-repository";

export class LoadEmployeeImports {
  constructor(private readonly imports: EmployeeImportRepository) {}
  execute(access: CompanyAccess, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canImportEmployees(access.permissions))
      return Promise.resolve(failed("employee_import_access_required"));
    if (!isEmployeeImportCursor(after)) return Promise.resolve(failed("invalid_page"));
    return this.imports.list(access.companyId, after?.toLowerCase() ?? null, signal);
  }
}
