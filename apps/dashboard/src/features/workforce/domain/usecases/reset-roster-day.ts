import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { RosterReset } from "../entities/roster-reset";
import { normalizeRosterReset } from "../policies/calendar-change-policy";
import { canManageCalendars } from "../policies/calendar-policy";
import type { CalendarRepository } from "../repositories/calendar-repository";

export class ResetRosterDay {
  constructor(private readonly calendars: CalendarRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: RosterReset,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!canManageCalendars(access.permissions)) return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_roster_change"));
    const change = normalizeRosterReset(input);
    return change.ok
      ? this.calendars.resetRoster(access.companyId, operation, change.value, signal)
      : Promise.resolve(change);
  }
}
