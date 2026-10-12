import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { type CompanyId, isUuid } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { AttendanceExceptionPage } from "../../domain/entities/attendance-exception-page";
import type { AttendanceExceptionSearch } from "../../domain/entities/attendance-exception-search";
import type { AttendanceExceptionPageDto } from "../models/attendance-exception-dto";

/** Translate the transport cursor and validate one exclusive, ordered response page. */
export function toAttendanceExceptionPage(
  dto: AttendanceExceptionPageDto,
  company: CompanyId,
  search: AttendanceExceptionSearch,
): AttendanceExceptionPage {
  const prefix = `1:${company}:${search.from}:${search.until}:${search.kind ?? "ALL"}:`;
  let previous: Readonly<{ workDate: string; employeeId: string }> | null = null;
  if (search.after !== null) {
    const parts = search.after.startsWith(prefix)
      ? search.after.slice(prefix.length).split(":")
      : [];
    const [workDate, employeeId] = parts;
    if (
      parts.length !== 2 ||
      !workDate ||
      !employeeId ||
      !isCalendarDate(workDate) ||
      workDate < search.from ||
      workDate > search.until ||
      !isUuid(employeeId) ||
      employeeId !== employeeId.toLowerCase()
    )
      throw new InvalidHttpResponseError();
    previous = { workDate, employeeId };
  }
  const items = dto.items.map((item) => {
    const employeeId = item.employeeId.toLowerCase() as EmployeeId;
    if (
      !item.employeeNumber.trim() ||
      !item.employeeName.trim() ||
      !isCalendarDate(item.workDate) ||
      item.workDate < search.from ||
      item.workDate > search.until ||
      utcInstantMicroseconds(item.lastReceivedAt) === null ||
      (item.pendingCount === 0 && !item.incomplete) ||
      (item.correctionVersion !== null && item.incomplete) ||
      (search.kind === "PENDING" && item.pendingCount === 0) ||
      (search.kind === "INCOMPLETE" && !item.incomplete) ||
      (previous &&
        (item.workDate > previous.workDate ||
          (item.workDate === previous.workDate && employeeId <= previous.employeeId)))
    )
      throw new InvalidHttpResponseError();
    previous = { workDate: item.workDate, employeeId };
    return Object.freeze({ ...item, employeeId });
  });
  const last = items.at(-1);
  if (
    dto.nextCursor !== null &&
    (items.length !== 50 ||
      !last ||
      dto.nextCursor !== `${prefix}${last.workDate}:${last.employeeId}`)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    companyId: company,
    search: Object.freeze({ ...search }),
    items: Object.freeze(items),
    nextCursor: dto.nextCursor,
  });
}
