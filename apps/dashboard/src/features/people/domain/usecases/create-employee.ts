import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeCreation } from "../entities/employee-creation";
import { canCreateEmployee, normalizeEmployeeCreation } from "../policies/employee-creation-policy";
import type { EmployeeRepository } from "../repositories/employee-repository";

export class CreateEmployee {
  constructor(private readonly employees: EmployeeRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: EmployeeCreation,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canCreateEmployee(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_employee_creation"));
    const normalized = normalizeEmployeeCreation(input);
    if (!normalized.ok) return Promise.resolve(normalized);
    return this.employees.create(access.companyId, operation, normalized.value, signal);
  }
}
