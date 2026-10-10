import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { Employee, EmployeeId } from "../entities/employee";
import { canReadEmployees, isEmployeeId } from "../policies/employee-policy";
import type { EmployeeRepository } from "../repositories/employee-repository";

export class LoadEmployee {
  constructor(private readonly employees: EmployeeRepository) {}
  execute(
    access: CompanyAccess,
    id: string,
    asOf: string,
    signal: AbortSignal,
  ): Promise<Result<Employee>> {
    signal.throwIfAborted();
    if (!canReadEmployees(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isEmployeeId(id)) return Promise.resolve(failed("employee_not_found"));
    if (!isCalendarDate(asOf)) return Promise.resolve(failed("invalid_employee_date"));
    return this.employees.get(access.companyId, id.toLowerCase() as EmployeeId, asOf, signal);
  }
}
