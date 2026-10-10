import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { isUuid } from "../../../../core/domain/identifiers";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../entities/employee";
import type { EmployeeRepository } from "../repositories/employee-repository";

export class LoadEmploymentDetails {
  constructor(private readonly employees: EmployeeRepository) {}
  execute(access: CompanyAccess, id: string, asOf: string, signal: AbortSignal) {
    signal.throwIfAborted();
    if (!access.permissions.includes("people.read"))
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("employee_not_found"));
    if (!isCalendarDate(asOf)) return Promise.resolve(failed("invalid_employee_date"));
    return this.employees.details(access.companyId, id.toLowerCase() as EmployeeId, asOf, signal);
  }
}
