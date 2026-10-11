import { isUuid } from "../../../../core/domain/identifiers";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { HolidayId, WorkHoliday } from "../entities/work-holiday";
import { canReadHolidays } from "../policies/holiday-policy";
import type { HolidayRepository } from "../repositories/holiday-repository";

export class LoadHoliday {
  constructor(private readonly holidays: HolidayRepository) {}
  execute(access: CompanyAccess, id: string, signal: AbortSignal): Promise<Result<WorkHoliday>> {
    signal.throwIfAborted();
    if (!canReadHolidays(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(id)) return Promise.resolve(failed("holiday_not_found"));
    return this.holidays.get(access.companyId, id.toLowerCase() as HolidayId, signal);
  }
}
