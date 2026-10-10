import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmploymentCancellation } from "../entities/employment-cancellation";
import { normalizeEmploymentCancellation } from "../policies/employment-cancellation-policy";
import { canManageEmployment } from "../policies/employment-policy";
import type { EmployeeRepository } from "../repositories/employee-repository";

export class CancelEmploymentRevision {
  constructor(private readonly employees: EmployeeRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: EmploymentCancellation,
    signal: AbortSignal,
  ) {
    signal.throwIfAborted();
    if (!canManageEmployment(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_revision_cancellation"));
    const normalized = normalizeEmploymentCancellation(input);
    if (!normalized.ok) return Promise.resolve(normalized);
    return this.employees.cancelRevision(access.companyId, operation, normalized.value, signal);
  }
}
