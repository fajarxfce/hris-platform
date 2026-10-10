import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeImportStart } from "../entities/employee-import-start";
import { normalizeEmployeeImportStart } from "../policies/employee-import-file-policy";
import { canImportEmployees } from "../policies/employee-import-policy";
import type { EmployeeImportRepository } from "../repositories/employee-import-repository";

export class StartEmployeeImport {
  constructor(private readonly imports: EmployeeImportRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: EmployeeImportStart,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canImportEmployees(access.permissions))
      return Promise.resolve(failed("employee_import_access_required"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_employee_import"));
    const normalized = normalizeEmployeeImportStart(input);
    if (!normalized.ok) return Promise.resolve(normalized);
    return this.imports.start(access.companyId, operation, normalized.value, signal);
  }
}
