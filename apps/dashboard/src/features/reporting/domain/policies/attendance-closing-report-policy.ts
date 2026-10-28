import type { AttendanceClosingEmployeeQuery } from "../entities/attendance-closing-report";
import { canReadAttendanceReport } from "./attendance-report-policy";
export const canReadAttendanceClosingReport = canReadAttendanceReport;
const month = /^(?:20\d{2}|2100)-(?:0[1-9]|1[0-2])$/u;
export function isAttendanceClosingReportRange(from: string, until: string): boolean {
  if (!month.test(from) || !month.test(until) || from > until) return false;
  return (
    (Number(until.slice(0, 4)) - Number(from.slice(0, 4))) * 12 +
      Number(until.slice(5)) -
      Number(from.slice(5)) <=
    11
  );
}
export const attendanceClosingReportRangeParameters = Object.freeze({
  maximumMonths: "12",
  minimumYear: "2000",
  maximumYear: "2100",
});
export function isAttendanceClosingEmployeePage(
  company: string,
  query: AttendanceClosingEmployeeQuery,
): boolean {
  if (query.after === null) return true;
  const prefix = `attendance-closings.v1|${company}|${query.month}|`;
  return (
    query.after.length <= 110 &&
    query.after.startsWith(prefix) &&
    /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/u.test(query.after.slice(prefix.length))
  );
}
