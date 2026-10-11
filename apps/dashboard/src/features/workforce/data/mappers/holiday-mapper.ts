import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { HolidayRange } from "../../domain/entities/holiday-range";
import type { HolidayId, WorkHoliday } from "../../domain/entities/work-holiday";
import type { HolidayDto } from "../models/holiday-dto";

export function toHoliday(
  dto: HolidayDto,
  companyId: CompanyId,
  expectedId?: HolidayId,
): WorkHoliday {
  const id = dto.id.toLowerCase() as HolidayId;
  if (
    !isCalendarDate(dto.workDate) ||
    !dto.name.trim() ||
    (expectedId !== undefined && id !== expectedId)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    id,
    companyId,
    workDate: dto.workDate,
    name: dto.name,
    active: dto.active,
    version: dto.version,
  });
}

export function toHolidays(
  dtos: readonly HolidayDto[],
  company: CompanyId,
  range: HolidayRange,
): readonly WorkHoliday[] {
  const holidays = dtos.map((dto) => toHoliday(dto, company));
  if (
    new Set(holidays.map((holiday) => holiday.id)).size !== holidays.length ||
    holidays.some(
      (holiday, index) =>
        holiday.workDate < range.from ||
        holiday.workDate > range.until ||
        (index > 0 && holiday.workDate <= (holidays[index - 1]?.workDate ?? "")),
    )
  )
    throw new InvalidHttpResponseError();
  return Object.freeze(holidays);
}
