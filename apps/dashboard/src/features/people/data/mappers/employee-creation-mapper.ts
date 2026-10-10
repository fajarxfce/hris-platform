import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { EmployeeCreation } from "../../domain/entities/employee-creation";
import type {
  EmployeeCreationDto,
  EmployeeCreationReceiptDto,
} from "../models/employee-creation-dto";

export function toEmployeeCreationDto(input: EmployeeCreation): EmployeeCreationDto {
  return {
    id: input.employeeId,
    employeeNumber: input.employeeNumber,
    person: {
      id: input.personId,
      legalName: input.legalName,
      birthDate: input.birthDate,
      nationality: input.nationality,
      email: input.email,
    },
    terms: {
      effectiveFrom: input.startDate,
      startDate: input.startDate,
      endDate: input.endDate,
      contract: input.contract,
      status: input.status,
      branchId: input.branchId,
      departmentId: input.departmentId,
      positionId: input.positionId,
      costCenterId: input.costCenterId,
      managerId: input.managerId,
    },
    reason: input.reason,
  };
}
export function toEmployeeCreationReceipt(
  dto: EmployeeCreationReceiptDto,
  input: EmployeeCreation,
): MutationReceipt {
  if (dto.id.toLowerCase() !== input.employeeId.toLowerCase() || dto.version !== 0)
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
