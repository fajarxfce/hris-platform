import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { EmploymentChange } from "../../domain/entities/employment-change";
import type {
  EmploymentChangeDto,
  EmploymentChangeReceiptDto,
} from "../models/employment-change-dto";

export function toEmploymentChangeDto(change: EmploymentChange): EmploymentChangeDto {
  return {
    version: change.expectedVersion,
    terms: {
      effectiveFrom: change.terms.effectiveFrom,
      startDate: change.terms.startDate,
      endDate: change.terms.endDate,
      contract: change.terms.contract,
      status: change.terms.status,
      branchId: change.terms.branchId,
      departmentId: change.terms.departmentId,
      positionId: change.terms.positionId,
      costCenterId: change.terms.costCenterId,
      managerId: change.terms.managerId,
    },
    reason: change.reason,
  };
}
export function toEmploymentChangeReceipt(
  dto: EmploymentChangeReceiptDto,
  change: EmploymentChange,
): MutationReceipt {
  if (
    dto.id.toLowerCase() !== change.employeeId.toLowerCase() ||
    dto.version !== change.expectedVersion + 1
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
