import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { HolidayChange } from "../../domain/entities/holiday-change";
import type { HolidayRange } from "../../domain/entities/holiday-range";
import type { HolidayId } from "../../domain/entities/work-holiday";
import type { HolidayRepository } from "../../domain/repositories/holiday-repository";
import type { HolidayDataSource } from "../datasources/holiday-data-source";
import { toHolidayChangeDto, toHolidayReceipt } from "../mappers/holiday-change-mapper";
import { toHoliday, toHolidays } from "../mappers/holiday-mapper";

export class RemoteHolidayRepository implements HolidayRepository {
  constructor(private readonly source: HolidayDataSource) {}
  list(company: CompanyId, range: HolidayRange, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toHolidays(await this.source.list(company, range, signal), company, range),
    );
  }
  get(company: CompanyId, id: HolidayId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toHoliday(await this.source.get(company, id, signal), company, id),
    );
  }
  save(company: CompanyId, operation: OperationId, change: HolidayChange, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toHolidayReceipt(
        await this.source.save(company, change.id, operation, toHolidayChangeDto(change), signal),
        change,
      ),
    );
  }
}
