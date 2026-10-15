import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveCalendarScope } from "../../domain/entities/leave-calendar";
import type { LeaveCalendarRepository } from "../../domain/repositories/leave-calendar-repository";
import type { LeaveCalendarDataSource } from "../datasources/leave-calendar-data-source";
import { toLeaveCalendar, toLeaveCalendarEntries } from "../mappers/leave-calendar-mapper";

export class RemoteLeaveCalendarRepository implements LeaveCalendarRepository {
  constructor(private readonly source: LeaveCalendarDataSource) {}
  month(company: CompanyId, scope: LeaveCalendarScope, month: string | null, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toLeaveCalendar(
        await this.source.month(company, scope, month, signal),
        company,
        scope,
        month,
      ),
    );
  }
  entries(
    company: CompanyId,
    scope: LeaveCalendarScope,
    date: string,
    after: string | null,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toLeaveCalendarEntries(
        await this.source.entries(company, scope, date, after, signal),
        company,
        scope,
        date,
        after,
      ),
    );
  }
}
