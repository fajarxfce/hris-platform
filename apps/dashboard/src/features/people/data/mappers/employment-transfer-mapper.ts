import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { CompanyMembership } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../../domain/entities/employee";
import type { EmploymentTransfer } from "../../domain/entities/employment-transfer";
import type { EmploymentTransferInput } from "../../domain/entities/employment-transfer-input";
import type { EmploymentTransferReview } from "../../domain/entities/employment-transfer-review";
import { isWorkingOn } from "../../domain/policies/employee-policy";
import type { EmploymentChangeReceiptDto } from "../models/employment-change-dto";
import type {
  EmploymentTransferDto,
  EmploymentTransferInputDto,
  EmploymentTransferReviewDto,
} from "../models/employment-transfer-dto";
import { toEmployee } from "./employee-mapper";

export function toTransferCompany(
  dto: EmploymentTransferReviewDto["sourceCompany"],
): CompanyMembership {
  if (!dto.code.trim() || !dto.name.trim() || !dto.timezone.trim())
    throw new InvalidHttpResponseError();
  return Object.freeze({
    id: dto.id.toLowerCase() as CompanyId,
    code: dto.code,
    name: dto.name,
    timezone: dto.timezone,
  });
}

export function toEmploymentTransferReview(
  dto: EmploymentTransferReviewDto,
  companyId: CompanyId,
  id: EmployeeId,
  targetCompanyId: CompanyId,
): EmploymentTransferReview {
  const sourceCompany = toTransferCompany(dto.sourceCompany);
  const targetCompany = toTransferCompany(dto.targetCompany);
  if (
    sourceCompany.id !== companyId ||
    targetCompany.id !== targetCompanyId ||
    targetCompanyId === companyId ||
    !isCalendarDate(dto.sourceDate) ||
    dto.sourceDate !== dto.targetDate ||
    new Set(dto.targetPermissions).size !== dto.targetPermissions.length ||
    !["people.manage", "people.transfer"].every((permission) =>
      dto.targetPermissions.includes(permission),
    )
  )
    throw new InvalidHttpResponseError();
  const employee = toEmployee(dto.employee, companyId, dto.sourceDate, id);
  if (!isWorkingOn(employee.terms, dto.sourceDate) || employee.terms.startDate >= dto.sourceDate)
    throw new InvalidHttpResponseError();
  return Object.freeze({
    employee,
    sourceCompany,
    targetCompany,
    sourceDate: dto.sourceDate,
    targetDate: dto.targetDate,
    evaluatedAt: dto.evaluatedAt,
    targetPermissions: Object.freeze([...dto.targetPermissions]),
  });
}

export function toEmploymentTransfers(
  dtos: EmploymentTransferDto[],
  companyId: CompanyId,
  id: EmployeeId,
): readonly EmploymentTransfer[] {
  const directions = new Set<string>();
  const identifiers = new Set<string>();
  const records = dtos.map((dto): EmploymentTransfer => {
    const source =
      dto.sourceCompanyId.toLowerCase() === companyId &&
      dto.sourceEmploymentId.toLowerCase() === id;
    const target =
      dto.targetCompanyId.toLowerCase() === companyId &&
      dto.targetEmploymentId.toLowerCase() === id;
    const direction = source ? "outgoing" : "incoming";
    const identifier = dto.id.toLowerCase();
    if (
      (!source && !target) ||
      dto.sourceCompanyId.toLowerCase() === dto.targetCompanyId.toLowerCase() ||
      !isCalendarDate(dto.effectiveDate) ||
      !dto.reason.trim() ||
      directions.has(direction) ||
      identifiers.has(identifier)
    )
      throw new InvalidHttpResponseError();
    directions.add(direction);
    identifiers.add(identifier);
    return Object.freeze({
      id: identifier,
      sourceCompanyId: dto.sourceCompanyId.toLowerCase() as CompanyId,
      sourceEmploymentId: dto.sourceEmploymentId.toLowerCase() as EmployeeId,
      targetCompanyId: dto.targetCompanyId.toLowerCase() as CompanyId,
      targetEmploymentId: dto.targetEmploymentId.toLowerCase() as EmployeeId,
      sourceVersion: dto.sourceVersion,
      effectiveDate: dto.effectiveDate,
      reason: dto.reason,
      recordedAt: dto.recordedAt,
    });
  });
  return Object.freeze(records);
}

export function toEmploymentTransferInputDto(
  input: EmploymentTransferInput,
): EmploymentTransferInputDto {
  return {
    targetCompanyId: input.targetCompanyId,
    targetEmploymentId: input.targetEmploymentId,
    expectedVersion: input.expectedVersion,
    employeeNumber: input.employeeNumber,
    reason: input.reason,
    terms: {
      effectiveFrom: input.terms.effectiveFrom,
      startDate: input.terms.startDate,
      endDate: input.terms.endDate,
      contract: input.terms.contract,
      status: input.terms.status,
      branchId: input.terms.branchId,
      departmentId: input.terms.departmentId,
      positionId: input.terms.positionId,
      costCenterId: input.terms.costCenterId,
      managerId: input.terms.managerId,
    },
  };
}

export function toEmploymentTransferReceipt(
  dto: EmploymentChangeReceiptDto,
  input: EmploymentTransferInput,
): MutationReceipt {
  if (dto.id.toLowerCase() !== input.targetEmploymentId.toLowerCase() || dto.version !== 0)
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
