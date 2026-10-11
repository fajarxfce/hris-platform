import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { HolidayChange } from "../entities/holiday-change";
import { normalizeHolidayChange } from "../policies/holiday-change-policy";
import { canManageHolidays } from "../policies/holiday-policy";
import type { HolidayRepository } from "../repositories/holiday-repository";

export class SaveHoliday {
  constructor(private readonly holidays: HolidayRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: HolidayChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!canManageHolidays(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_holiday"));
    const change = normalizeHolidayChange(input);
    if (!change.ok) return Promise.resolve(change);
    return this.holidays.save(access.companyId, operation, change.value, signal);
  }
}
