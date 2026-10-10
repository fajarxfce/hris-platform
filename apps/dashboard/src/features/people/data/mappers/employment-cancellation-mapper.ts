import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { EmploymentCancellation } from "../../domain/entities/employment-cancellation";
import type { EmploymentCancellationDto } from "../models/employment-cancellation-dto";
import type { EmploymentChangeReceiptDto } from "../models/employment-change-dto";

export function toEmploymentCancellationDto(
  input: EmploymentCancellation,
): EmploymentCancellationDto {
  return { expectedVersion: input.expectedVersion, reason: input.reason };
}
export function toEmploymentCancellationReceipt(
  dto: EmploymentChangeReceiptDto,
  input: EmploymentCancellation,
): MutationReceipt {
  if (
    dto.id.toLowerCase() !== input.employeeId.toLowerCase() ||
    dto.version !== input.expectedVersion + 1
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
