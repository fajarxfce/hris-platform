import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeImportApplication } from "../entities/employee-import-change";
import { normalizeEmployeeImportApplication } from "../policies/employee-import-change-policy";
import { canImportEmployees } from "../policies/employee-import-policy";
import type { EmployeeImportRepository } from "../repositories/employee-import-repository";

export class ApplyEmployeeImport {
  constructor(private readonly imports: EmployeeImportRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: EmployeeImportApplication,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canImportEmployees(access.permissions))
      return Promise.resolve(failed("employee_import_access_required"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_employee_import"));
    const change = normalizeEmployeeImportApplication(input);
    if (!change.ok) return Promise.resolve(change);
    return this.imports.apply(access.companyId, operation, change.value, signal);
  }
}
