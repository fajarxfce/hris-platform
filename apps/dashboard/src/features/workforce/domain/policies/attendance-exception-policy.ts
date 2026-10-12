import { failed, type Result, success } from "../../../../core/domain/result";
import type {
  AttendanceExceptionSearch,
  AttendanceExceptionSearchInput,
} from "../entities/attendance-exception-search";
import { parseAttendanceRange } from "./attendance-policy";

export function parseAttendanceExceptionSearch(
  input: AttendanceExceptionSearchInput,
): Result<AttendanceExceptionSearch> {
  const range = parseAttendanceRange(input);
  if (!range.ok) return range;
  const { kind, after } = input;
  if (kind !== null && kind !== "PENDING" && kind !== "INCOMPLETE")
    return failed("invalid_attendance_filter");
  if (after !== null && (after.length === 0 || after.length > 160)) return failed("invalid_page");
  return success(Object.freeze({ from: range.value.from, until: range.value.until, kind, after }));
}
