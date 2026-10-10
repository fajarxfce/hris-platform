import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Employee, EmployeeId } from "../../domain/entities/employee";
import type { EmployeePage } from "../../domain/entities/employee-page";
import type { EmployeeSearch } from "../../domain/entities/employee-search";
import { isEmployeeNumber } from "../../domain/policies/employee-policy";
import type { EmployeeDto, EmployeePageDto } from "../models/employee-dto";
import { toEmploymentTerms } from "./employment-terms-mapper";

export function toEmployee(
  dto: EmployeeDto,
  companyId: CompanyId,
  asOf: string,
  id?: EmployeeId,
): Employee {
  if (
    dto.companyId.toLowerCase() !== companyId ||
    (id !== undefined && dto.id.toLowerCase() !== id) ||
    !isEmployeeNumber(dto.employeeNumber) ||
    dto.person.legalName.trim().length === 0 ||
    dto.appliedRevision > dto.version ||
    dto.terms.effectiveFrom > asOf
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    id: dto.id.toLowerCase() as EmployeeId,
    companyId,
    employeeNumber: dto.employeeNumber,
    legalName: dto.person.legalName,
    email: dto.person.email,
    terms: toEmploymentTerms(dto.terms),
    version: dto.version,
    appliedRevision: dto.appliedRevision,
  });
}

export function toEmployeePage(
  dto: EmployeePageDto,
  companyId: CompanyId,
  search: EmployeeSearch,
): EmployeePage {
  const items = dto.items.map((item) => toEmployee(item, companyId, search.asOf));
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    new Set(items.map((item) => item.employeeNumber)).size !== items.length ||
    items.some((item) => item.employeeNumber === search.after) ||
    (dto.nextCursor !== null &&
      (items.length !== 50 || dto.nextCursor !== items.at(-1)?.employeeNumber))
  )
    throw new InvalidHttpResponseError();
  // Employee-number order follows the database collation, not JavaScript string comparison.
  return Object.freeze({ items: Object.freeze(items), nextCursor: dto.nextCursor });
}
