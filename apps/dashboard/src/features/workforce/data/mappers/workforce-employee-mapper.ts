import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import { isEmployeeNumber } from "../../../people/domain/policies/employee-policy";
import type { WorkforceEmployee } from "../../domain/entities/workforce-employee";
import type { WorkforceEmployeePage } from "../../domain/entities/workforce-employee-page";
import type { WorkforceEmployeeDto, WorkforceEmployeePageDto } from "../models/calendar-dto";

export function toWorkforceEmployee(
  dto: WorkforceEmployeeDto,
  company: CompanyId,
): WorkforceEmployee {
  if (!isEmployeeNumber(dto.employeeNumber) || !dto.legalName.trim())
    throw new InvalidHttpResponseError();
  return Object.freeze({ ...dto, id: dto.id.toLowerCase() as EmployeeId, companyId: company });
}
export function toWorkforceEmployeePage(
  dto: WorkforceEmployeePageDto,
  company: CompanyId,
  after: string | null,
): WorkforceEmployeePage {
  const items = dto.items.map((item) => toWorkforceEmployee(item, company));
  if (
    new Set(items.map((item) => item.id)).size !== items.length ||
    new Set(items.map((item) => item.employeeNumber)).size !== items.length ||
    items.some((item) => item.employeeNumber === after) ||
    (dto.nextCursor !== null &&
      (!isEmployeeNumber(dto.nextCursor) ||
        items.length !== 50 ||
        dto.nextCursor !== items.at(-1)?.employeeNumber ||
        dto.nextCursor === after))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    items: Object.freeze(items),
    nextCursor: dto.nextCursor,
    bulkRosterSupported: dto.bulkRosterSupported ?? false,
  });
}
