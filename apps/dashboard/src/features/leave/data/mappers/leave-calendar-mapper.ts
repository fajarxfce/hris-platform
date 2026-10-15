import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { LeaveCalendar, LeaveCalendarScope } from "../../domain/entities/leave-calendar";
import type { LeaveCalendarEntries } from "../../domain/entities/leave-calendar-entry";
import type { LeaveRequestId } from "../../domain/entities/leave-request";
import {
  isLeaveCalendarDate,
  isLeaveCalendarMonth,
} from "../../domain/policies/leave-calendar-policy";
import type { LeaveCalendarDto, LeaveCalendarEntriesDto } from "../models/leave-calendar-dto";

export function toLeaveCalendar(
  raw: LeaveCalendarDto,
  companyId: CompanyId,
  scope: LeaveCalendarScope,
  selected: string | null,
): LeaveCalendar {
  if (
    raw.scope !== scope ||
    !isLeaveCalendarDate(raw.companyDate) ||
    !isLeaveCalendarMonth(raw.month) ||
    raw.month !== (selected ?? raw.companyDate.slice(0, 7))
  )
    throw new InvalidHttpResponseError();
  let previous = "";
  const days = raw.days.map((day) => {
    if (
      !isLeaveCalendarDate(day.date) ||
      !day.date.startsWith(`${raw.month}-`) ||
      day.date <= previous ||
      day.requestCount < day.employeeCount ||
      day.requestCount > day.employeeCount * 2 ||
      day.pendingCount + day.approvedCount + day.cancellationPendingCount !== day.requestCount
    )
      throw new InvalidHttpResponseError();
    previous = day.date;
    return Object.freeze({ ...day });
  });
  return Object.freeze({ ...raw, companyId, days: Object.freeze(days) });
}

export function toLeaveCalendarEntries(
  raw: LeaveCalendarEntriesDto,
  companyId: CompanyId,
  scope: LeaveCalendarScope,
  date: string,
  after: string | null,
): LeaveCalendarEntries {
  if (raw.scope !== scope || raw.date !== date || !isLeaveCalendarDate(raw.companyDate))
    throw new InvalidHttpResponseError();
  let previous = after;
  const items = raw.entries.items.map((item) => {
    const id = item.requestId.toLowerCase();
    if (previous !== null && id <= previous) throw new InvalidHttpResponseError();
    previous = id;
    return Object.freeze({
      ...item,
      requestId: id as LeaveRequestId,
      employeeId: item.employeeId.toLowerCase(),
    });
  });
  const nextCursor = raw.entries.nextCursor?.toLowerCase() ?? null;
  if (nextCursor !== null && (items.length !== 20 || nextCursor !== items.at(-1)?.requestId))
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...raw,
    companyId,
    entries: Object.freeze({ items: Object.freeze(items), nextCursor }),
  });
}
