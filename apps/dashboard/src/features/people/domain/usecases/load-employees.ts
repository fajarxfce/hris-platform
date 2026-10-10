import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeePage } from "../entities/employee-page";
import type { EmployeeSearch } from "../entities/employee-search";
import { canReadEmployees, validateEmployeeSearch } from "../policies/employee-policy";
import type { EmployeeRepository } from "../repositories/employee-repository";

export class LoadEmployees {
  constructor(private readonly employees: EmployeeRepository) {}
  execute(
    access: CompanyAccess,
    search: EmployeeSearch,
    signal: AbortSignal,
  ): Promise<Result<EmployeePage>> {
    signal.throwIfAborted();
    if (!canReadEmployees(access.permissions)) return Promise.resolve(failed("access_denied"));
    const failure = validateEmployeeSearch(search);
    if (failure) return Promise.resolve({ ok: false, failure });
    return this.employees.list(
      access.companyId,
      Object.freeze({ ...search, query: search.query.trim() }),
      signal,
    );
  }
}
