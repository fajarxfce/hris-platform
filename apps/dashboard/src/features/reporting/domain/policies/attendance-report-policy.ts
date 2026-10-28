import { isHeadcountDate } from "./headcount-policy";

export function canReadAttendanceReport(permissions: readonly string[]): boolean {
  return permissions.includes("reports.read") && permissions.includes("workforce.read");
}

export function isAttendanceReportRange(from: string, until: string): boolean {
  if (!isHeadcountDate(from) || !isHeadcountDate(until)) return false;
  const days = (Date.parse(`${until}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000;
  return days >= 0 && days <= 30;
}
