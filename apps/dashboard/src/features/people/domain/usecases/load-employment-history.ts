import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../entities/employee";
import type { EmploymentHistoryPage } from "../entities/employment-revision";
import {
  canReadEmploymentHistory,
  isEmployeeId,
  isEmploymentRevisionCursor,
} from "../policies/employee-policy";
import type { EmployeeRepository } from "../repositories/employee-repository";

export class LoadEmploymentHistory {
  constructor(private readonly employees: EmployeeRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    after: string | null,
    signal: AbortSignal,
  ): Promise<Result<EmploymentHistoryPage>> {
    signal.throwIfAborted();
    if (!canReadEmploymentHistory(access.permissions))
      return Promise.resolve(failed("access_denied"));
    if (!isEmployeeId(id)) return Promise.resolve(failed("employee_not_found"));
    if (after !== null && !isEmploymentRevisionCursor(after))
      return Promise.resolve(failed("invalid_page"));
    return this.employees.history(access.companyId, id.toLowerCase() as EmployeeId, after, signal);
  }
}
