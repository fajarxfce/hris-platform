import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../entities/employee";
import { isEmploymentRevisionCursor } from "../policies/employee-policy";
import type { EmployeeRepository } from "../repositories/employee-repository";

export class LoadEmploymentRevision {
  constructor(private readonly employees: EmployeeRepository) {}
  execute(access: CompanyAccess, id: string, revision: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!access.permissions.includes("people.read"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("employee_not_found"));
    if (!isEmploymentRevisionCursor(revision))
      return Promise.resolve(failed("invalid_employment_revision"));
    return this.employees.revision(
      access.companyId,
      id.toLowerCase() as EmployeeId,
      Number(revision),
      signal,
    );
  }
}
