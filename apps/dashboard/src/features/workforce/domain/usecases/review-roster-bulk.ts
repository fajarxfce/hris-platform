import type { Result } from "../../../../core/domain/result";
import { failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { RosterBulkReview } from "../entities/roster-bulk-review";
import type { RosterBulkSelection } from "../entities/roster-bulk-selection";
import { canReadCalendars } from "../policies/calendar-policy";
import { normalizeRosterBulkSelection } from "../policies/roster-bulk-policy";
import type { CalendarRepository } from "../repositories/calendar-repository";

export class ReviewRosterBulk {
  constructor(private readonly calendars: CalendarRepository) {}
  execute(
    access: CompanyAccess,
    input: RosterBulkSelection,
    signal: AbortSignal,
  ): Promise<Result<RosterBulkReview>> {
    signal.throwIfAborted();
    if (!canReadCalendars(access.permissions)) return Promise.resolve(failed("access_denied"));
    const selection = normalizeRosterBulkSelection(input);
    return selection.ok
      ? this.calendars.reviewBulk(access.companyId, selection.value, signal)
      : Promise.resolve(selection);
  }
}
