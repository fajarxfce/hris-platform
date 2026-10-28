import { isCalendarDate } from "../../../../core/domain/calendar-date";
import { canBrowseCompanyOvertime } from "../../../workforce/domain/policies/overtime-read-policy";

export function canReadOvertimeReport(permissions: readonly string[]): boolean {
  return permissions.includes("reports.read") && canBrowseCompanyOvertime(permissions);
}
export function isOvertimeReportRange(from: string, until: string): boolean {
  return (
    isCalendarDate(from) &&
    isCalendarDate(until) &&
    from >= "2000-01-01" &&
    until <= "2100-12-31" &&
    from <= until &&
    (Date.parse(`${until}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000 <= 365
  );
}
export const overtimeReportRangeParameters = Object.freeze({
  maximumDays: "366",
  minimumYear: "2000",
  maximumYear: "2100",
});
