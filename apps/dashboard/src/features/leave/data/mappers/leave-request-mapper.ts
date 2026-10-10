import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type {
  LeaveRequestId,
  LeaveRequestPage,
  LeaveRequestQuery,
} from "../../domain/entities/leave-request";
import type { LeaveRequestPageDto } from "../models/leave-request-summary-dto";

export function toLeaveRequestPage(
  dto: LeaveRequestPageDto,
  companyId: CompanyId,
  query: LeaveRequestQuery,
): LeaveRequestPage {
  let previous: { at: bigint; id: string } | null = null;
  const seen = new Set<string>();
  const items = dto.items.map((item) => {
    const at = utcInstantMicroseconds(item.submittedAt);
    const id = item.id.toLowerCase() as LeaveRequestId;
    if (
      at === null ||
      seen.has(id) ||
      id === query.after ||
      !isCalendarDate(item.from) ||
      !isCalendarDate(item.until) ||
      item.from > item.until ||
      (query.employeeId !== null && item.employeeId.toLowerCase() !== query.employeeId) ||
      (query.status !== null && item.status !== query.status) ||
      (previous !== null && (at > previous.at || (at === previous.at && id >= previous.id)))
    )
      throw new InvalidHttpResponseError();
    seen.add(id);
    previous = { at, id };
    return Object.freeze({ ...item, id, companyId, employeeId: item.employeeId.toLowerCase() });
  });
  const nextCursor = dto.nextCursor?.toLowerCase() ?? null;
  if (
    nextCursor !== null &&
    (items.length !== 20 || nextCursor !== items.at(-1)?.id || nextCursor === query.after)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ items: Object.freeze(items), nextCursor });
}
