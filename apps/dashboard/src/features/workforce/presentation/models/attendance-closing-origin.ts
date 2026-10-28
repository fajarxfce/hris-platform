import type { CompanyId } from "../../../../core/domain/identifiers";

export type AttendanceClosingOrigin = Readonly<{
  to: string;
  from: string;
  until: string;
  after: string | null;
}>;
/** Return context selects a report view; current company/source grants still authorize each read. */
export function attendanceClosingOrigin(
  params: URLSearchParams,
  company: CompanyId,
  month: string,
): AttendanceClosingOrigin | null {
  const from = params.get("reportFrom"),
    until = params.get("reportUntil"),
    after = params.get("reportAfter");
  const valid = /^(?:20\d{2}|2100)-(?:0[1-9]|1[0-2])$/u;
  if (
    params.get("company") !== company ||
    !from ||
    !until ||
    !valid.test(month) ||
    !valid.test(from) ||
    !valid.test(until) ||
    from > month ||
    until < month
  )
    return null;
  if (
    (Number(until.slice(0, 4)) - Number(from.slice(0, 4))) * 12 +
      Number(until.slice(5)) -
      Number(from.slice(5)) >
    11
  )
    return null;
  const prefix = `attendance-closings.v1|${company}|${month}|`;
  if (
    after !== null &&
    (after.length > 110 ||
      !after.startsWith(prefix) ||
      !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/u.test(after.slice(prefix.length)))
  )
    return null;
  const query = new URLSearchParams({ company, month, from, until });
  if (after !== null) query.set("after", after);
  return Object.freeze({
    to: `/reports/attendance-closings/employees?${query}`,
    from,
    until,
    after,
  });
}
export function withAttendanceClosingOrigin(
  target: string,
  origin: AttendanceClosingOrigin | null,
): string {
  if (!origin) return target;
  const [path, search] = target.split("?");
  const params = new URLSearchParams(search);
  params.set("reportFrom", origin.from);
  params.set("reportUntil", origin.until);
  if (origin.after !== null) params.set("reportAfter", origin.after);
  return `${path}?${params}`;
}
