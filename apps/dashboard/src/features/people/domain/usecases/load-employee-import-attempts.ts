import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeImportId } from "../entities/employee-import";
import { canImportEmployees, isEmployeeImportCursor } from "../policies/employee-import-policy";
import type { EmployeeImportRepository } from "../repositories/employee-import-repository";

export class LoadEmployeeImportAttempts {
  constructor(private readonly imports: EmployeeImportRepository) {}
  execute(access: CompanyAccess, id: string, after: string | null, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!canImportEmployees(access.permissions))
      return Promise.resolve(failed("employee_import_access_required"));
    if (!isUuid(id)) return Promise.resolve(failed("employee_import_not_found"));
    if (!isEmployeeImportCursor(after)) return Promise.resolve(failed("invalid_page"));
    return this.imports.attempts(
      access.companyId,
      id.toLowerCase() as EmployeeImportId,
      after?.toLowerCase() ?? null,
      signal,
    );
  }
}
