import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { HolidayRange } from "../entities/holiday-range";
import type { WorkHoliday } from "../entities/work-holiday";
import { canReadHolidays, validateHolidayRange } from "../policies/holiday-policy";
import type { HolidayRepository } from "../repositories/holiday-repository";

export class LoadHolidays {
  constructor(private readonly holidays: HolidayRepository) {}
  execute(
    access: CompanyAccess,
    input: HolidayRange,
    signal: AbortSignal,
  ): Promise<Result<readonly WorkHoliday[]>> {
    signal.throwIfAborted();
    if (!canReadHolidays(access.permissions)) return Promise.resolve(failed("access_denied"));
    const range = validateHolidayRange(input);
    if (!range.ok) return Promise.resolve(range);
    return this.holidays.list(access.companyId, range.value, signal);
  }
}
