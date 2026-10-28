import { leaveStatuses } from "../../../leave/domain/entities/leave-request";
import type { LeaveReportQuery } from "../entities/leave-report-request";

export function canReadLeaveReport(permissions: readonly string[]): boolean {
  return permissions.includes("reports.read") && permissions.includes("leave.read");
}

export function isLeaveReportRange(from: string, until: string): boolean {
  const valid = (value: string) => {
    if (!/^(?:19\d{2}|2[01]\d{2}|2200)-\d{2}-\d{2}$/u.test(value)) return false;
    const date = new Date(`${value}T00:00:00Z`);
    return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
  };
  if (!valid(from) || !valid(until)) return false;
  const days = (Date.parse(`${until}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000;
  return days >= 0 && days <= 365;
}

export function leaveReportCursorPrefix(
  company: string,
  query: Pick<LeaveReportQuery, "from" | "until" | "status">,
): string {
  return `1|${company}|${query.from}|${query.until}|${query.status ?? "ALL"}|`;
}

export function isLeaveReportPage(company: string, query: LeaveReportQuery): boolean {
  if (query.status !== null && !leaveStatuses.includes(query.status)) return false;
  if (query.after === null) return true;
  const prefix = leaveReportCursorPrefix(company, query);
  if (query.after.length > 200 || !query.after.startsWith(prefix)) return false;
  return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z\|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u.test(
    query.after.slice(prefix.length),
  );
}
