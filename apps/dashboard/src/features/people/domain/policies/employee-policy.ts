import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { Failure } from "../../../../core/domain/result";
import type { Employee } from "../entities/employee";
import type { EmployeeSearch } from "../entities/employee-search";
import type { EmploymentTerms } from "../entities/employment-terms";

export const isWorkingOn = (terms: EmploymentTerms, date: string): boolean =>
  (terms.status === "ACTIVE" || terms.status === "PROBATION") &&
  terms.startDate <= date &&
  (terms.endDate === null || terms.endDate >= date);

export const isEligibleManager = (
  employee: Employee,
  effectiveDate: string,
  excludedId?: string,
): boolean =>
  employee.id.toLowerCase() !== excludedId?.toLowerCase() &&
  isWorkingOn(employee.terms, effectiveDate);

export const canReadEmployees = (permissions: readonly string[]): boolean =>
  permissions.some((permission) =>
    ["people.read", "people.team.read", "people.self.read"].includes(permission),
  );

export const canReadEmploymentHistory = (permissions: readonly string[]): boolean =>
  permissions.includes("people.read");

export const isEmployeeId = (id: string): boolean =>
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/iu.test(id);

export const isEmployeeNumber = (value: string): boolean =>
  /^[A-Z0-9][A-Z0-9_-]{1,31}$/u.test(value);

export const isEmploymentRevisionCursor = (value: string): boolean =>
  /^(0|[1-9]\d{0,15})$/u.test(value) && Number.isSafeInteger(Number(value));

export function validateEmployeeSearch(search: EmployeeSearch): Failure | null {
  if (!isCalendarDate(search.asOf))
    return { code: "invalid_employee_date", fields: {}, parameters: {} };
  if (search.query.length > 120)
    return { code: "invalid_employee_search", fields: {}, parameters: {} };
  if (search.after !== null && !isEmployeeNumber(search.after))
    return { code: "invalid_page", fields: {}, parameters: {} };
  return null;
}
