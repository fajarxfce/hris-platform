import type { CompanyId } from "../../../../core/domain/identifiers";
import type { AttendanceExceptionSearchInput } from "../../domain/entities/attendance-exception-search";

export const attendanceExceptionsPath = "/workforce/attendance/exceptions";

export function attendanceExceptionSearch(
  parameters: URLSearchParams,
  company: CompanyId,
  today: string,
): AttendanceExceptionSearchInput {
  const from = new Date(
    Math.max(Date.parse(`${today}T00:00:00Z`) - 6 * 86_400_000, Date.parse("0001-01-01T00:00:00Z")),
  )
    .toISOString()
    .slice(0, 10);
  if (parameters.has("company") && parameters.get("company") !== company)
    return Object.freeze({ from, until: today, kind: null, after: null });
  return Object.freeze({
    from: parameters.get("from") ?? from,
    until: parameters.get("until") ?? today,
    kind: parameters.get("kind"),
    after: parameters.get("after"),
  });
}

export function attendanceExceptionParameters(
  company: CompanyId,
  search: AttendanceExceptionSearchInput,
): URLSearchParams {
  const parameters = new URLSearchParams({ company, from: search.from, until: search.until });
  if (search.kind !== null) parameters.set("kind", search.kind);
  if (search.after !== null) parameters.set("after", search.after);
  return parameters;
}
