import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeImportId } from "../entities/employee-import";
import { canImportEmployees } from "../policies/employee-import-policy";
import type { EmployeeImportRepository } from "../repositories/employee-import-repository";

export class LoadEmployeeImport {
  constructor(private readonly imports: EmployeeImportRepository) {}
  execute(access: CompanyAccess, id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canImportEmployees(access.permissions))
      return Promise.resolve(failed("employee_import_access_required"));
    if (!isUuid(id)) return Promise.resolve(failed("employee_import_not_found"));
    return this.imports.summary(access.companyId, id.toLowerCase() as EmployeeImportId, signal);
  }
}
