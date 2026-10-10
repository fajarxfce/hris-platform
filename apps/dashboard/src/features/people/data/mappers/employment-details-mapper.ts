import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { OrganizationUnitId } from "../../../organization/domain/entities/organization-unit";
import { isOrganizationUnitCode } from "../../../organization/domain/policies/organization-unit-policy";
import type { EmployeeId } from "../../domain/entities/employee";
import type {
  EmploymentDetails,
  EmploymentUnitReference,
} from "../../domain/entities/employment-details";
import { isEmployeeNumber } from "../../domain/policies/employee-policy";
import type { EmploymentDetailsDto } from "../models/employment-details-dto";
import { toEmployee } from "./employee-mapper";

export function toEmploymentUnitReference(
  dto: EmploymentDetailsDto["branch"],
  id: string | null,
): EmploymentUnitReference | null {
  if (!dto) return null;
  if (
    dto.id.toLowerCase() !== id?.toLowerCase() ||
    !dto.name.trim() ||
    !isOrganizationUnitCode(dto.code)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    id: dto.id.toLowerCase() as OrganizationUnitId,
    code: dto.code,
    name: dto.name,
    active: dto.active,
  });
}
export function toEmploymentDetails(
  dto: EmploymentDetailsDto,
  company: CompanyId,
  id: EmployeeId,
  asOf: string,
): EmploymentDetails {
  if (dto.asOf !== asOf) throw new InvalidHttpResponseError();
  const employee = toEmployee(dto.employee, company, asOf, id);
  const terms = Object.freeze({
    ...employee.terms,
    branchId: (dto.employee.terms.branchId?.toLowerCase() as OrganizationUnitId) ?? null,
    departmentId: (dto.employee.terms.departmentId?.toLowerCase() as OrganizationUnitId) ?? null,
    positionId: (dto.employee.terms.positionId?.toLowerCase() as OrganizationUnitId) ?? null,
    costCenterId: (dto.employee.terms.costCenterId?.toLowerCase() as OrganizationUnitId) ?? null,
    managerId: (dto.employee.terms.managerId?.toLowerCase() as EmployeeId) ?? null,
  });
  if (
    dto.manager &&
    (dto.manager.id.toLowerCase() !== terms.managerId ||
      !dto.manager.legalName.trim() ||
      !isEmployeeNumber(dto.manager.employeeNumber))
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    asOf,
    employee: Object.freeze({ ...employee, terms }),
    branch: toEmploymentUnitReference(dto.branch, terms.branchId),
    department: toEmploymentUnitReference(dto.department, terms.departmentId),
    position: toEmploymentUnitReference(dto.position, terms.positionId),
    costCenter: toEmploymentUnitReference(dto.costCenter, terms.costCenterId),
    manager: dto.manager
      ? Object.freeze({
          id: dto.manager.id.toLowerCase() as EmployeeId,
          employeeNumber: dto.manager.employeeNumber,
          legalName: dto.manager.legalName,
          working: dto.manager.working,
        })
      : null,
  });
}
