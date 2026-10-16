import { isUuid, type OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { RosterBulkChange } from "../entities/roster-bulk-change";
import { canManageCalendars } from "../policies/calendar-policy";
import { normalizeRosterBulkChange } from "../policies/roster-bulk-policy";
import type { CalendarRepository } from "../repositories/calendar-repository";

export class ApplyRosterBulk {
  constructor(private readonly calendars: CalendarRepository) {}
  execute(
    access: CompanyAccess,
    operation: OperationId,
    input: RosterBulkChange,
    signal: AbortSignal,
  ): Promise<Result<MutationReceipt>> {
    signal.throwIfAborted();
    if (!canManageCalendars(access.permissions) || input.companyId !== access.companyId)
      return Promise.resolve(failed("access_denied"));
    if (!isUuid(operation)) return Promise.resolve(failed("invalid_roster_bulk_change"));
    const change = normalizeRosterBulkChange(input);
    return change.ok
      ? this.calendars.applyBulk(access.companyId, operation, change.value, signal)
      : Promise.resolve(change);
  }
}
