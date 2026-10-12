import { toAttendanceExceptionPage } from "../data/mappers/attendance-exception-page-mapper";
import type {
  AttendanceExceptionDto,
  AttendanceExceptionPageDto,
} from "../data/models/attendance-exception-dto";
import type { AttendanceExceptionSearch } from "../domain/entities/attendance-exception-search";
import { company, identifier } from "./attendance-fixture";

export const exceptionSearch: AttendanceExceptionSearch = {
  from: "2026-10-01",
  until: "2026-10-31",
  kind: null,
  after: null,
};
export function attendanceExceptionDtoFixture(index = 0): AttendanceExceptionDto {
  return {
    employeeId: identifier(501 + index),
    employeeNumber: `EMP-${String(index + 1).padStart(3, "0")}`,
    employeeName: index === 0 ? "North Employee" : `Employee ${index + 1}`,
    workDate: index < 26 ? "2026-10-12" : "2026-10-11",
    pendingCount: index % 4 === 1 ? 0 : 1,
    incomplete: index % 4 === 1 || index % 4 === 3,
    correctionVersion: index % 4 === 2 ? 0 : null,
    lastReceivedAt: "2026-10-13T03:15:00Z",
  };
}
export function attendanceExceptionCursorFixture(
  search: AttendanceExceptionSearch,
  item: AttendanceExceptionDto,
  scope: string = company,
): string {
  return `1:${scope}:${search.from}:${search.until}:${search.kind ?? "ALL"}:${item.workDate}:${item.employeeId}`;
}
export function attendanceExceptionPageDtoFixture(
  search: AttendanceExceptionSearch = exceptionSearch,
  count = 53,
): AttendanceExceptionPageDto {
  if (count < 0 || count > 200) throw new Error("Invalid exception fixture size");
  const cursor = search.after?.split(":").slice(-2);
  const rows = Array.from({ length: count }, (_, index) => attendanceExceptionDtoFixture(index))
    .filter((item) => item.workDate >= search.from && item.workDate <= search.until)
    .filter((item) => search.kind !== "PENDING" || item.pendingCount > 0)
    .filter((item) => search.kind !== "INCOMPLETE" || item.incomplete)
    .filter(
      (item) =>
        !cursor ||
        item.workDate < (cursor[0] ?? "") ||
        (item.workDate === cursor[0] && item.employeeId > (cursor[1] ?? "")),
    );
  const items = rows.slice(0, 50);
  const last = items.at(-1);
  return {
    items,
    nextCursor: rows.length > 50 && last ? attendanceExceptionCursorFixture(search, last) : null,
  };
}
export const attendanceExceptionPageFixture = (search = exceptionSearch) =>
  toAttendanceExceptionPage(attendanceExceptionPageDtoFixture(search), company, search);
