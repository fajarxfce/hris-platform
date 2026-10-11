import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { RosterDayChange } from "../entities/roster-day-change";
import { normalizeRosterDay } from "../policies/calendar-change-policy";
import { canManageCalendars } from "../policies/calendar-policy";
import type { CalendarRepository } from "../repositories/calendar-repository";

export class SetRosterDay {
  constructor(private readonly calendars: CalendarRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: RosterDayChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!canManageCalendars(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_roster_change"));
    const change = normalizeRosterDay(input);
    return change.ok
      ? this.calendars.roster(access.companyId, operation, change.value, signal)
      : Promise.resolve(change);
  }
}
