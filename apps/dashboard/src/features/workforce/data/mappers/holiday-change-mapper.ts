import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { HolidayChange } from "../../domain/entities/holiday-change";
import type { HolidayChangeDto, HolidayReceiptDto } from "../models/holiday-dto";

export function toHolidayChangeDto(change: HolidayChange): HolidayChangeDto {
  return {
    workDate: change.workDate,
    name: change.name,
    active: change.active,
    expectedVersion: change.expectedVersion,
    reason: change.reason,
  };
}
export function toHolidayReceipt(dto: HolidayReceiptDto, change: HolidayChange): MutationReceipt {
  if (
    dto.id.toLowerCase() !== change.id ||
    dto.version !== (change.expectedVersion === null ? 0 : change.expectedVersion + 1)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
