import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Result } from "../../../../core/domain/result";
import type { HolidayChange } from "../entities/holiday-change";
import type { HolidayRange } from "../entities/holiday-range";
import type { HolidayId, WorkHoliday } from "../entities/work-holiday";

export interface HolidayRepository {
  list(
    company: CompanyId,
    range: HolidayRange,
    signal: AbortSignal,
  ): Promise<Result<readonly WorkHoliday[]>>;
  get(company: CompanyId, id: HolidayId, signal: AbortSignal): Promise<Result<WorkHoliday>>;
  save(
    company: CompanyId,
    operation: OperationId,
    change: HolidayChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>>;
}
