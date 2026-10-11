import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { ShiftChange } from "../../domain/entities/shift-change";
import type { ShiftChangeDto, ShiftReceiptDto } from "../models/shift-dto";

export function toShiftChangeDto(change: ShiftChange): ShiftChangeDto {
  return {
    ...change.details,
    active: change.active,
    expectedVersion: change.expectedVersion,
    reason: change.reason,
  };
}
export function toShiftReceipt(dto: ShiftReceiptDto, change: ShiftChange): MutationReceipt {
  if (
    dto.id.toLowerCase() !== change.id ||
    dto.version !== (change.expectedVersion === null ? 0 : change.expectedVersion + 1)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
