import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmploymentChange } from "../entities/employment-change";
import { canManageEmployment, normalizeEmploymentChange } from "../policies/employment-policy";
import type { EmployeeRepository } from "../repositories/employee-repository";

export class ReviseEmployment {
  constructor(private readonly employees: EmployeeRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    change: EmploymentChange,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManageEmployment(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_employment_change"));
    const normalized = normalizeEmploymentChange(change);
    if (!normalized.ok) return Promise.resolve(normalized);
    return this.employees.revise(access.companyId, operation, normalized.value, signal);
  }
}
