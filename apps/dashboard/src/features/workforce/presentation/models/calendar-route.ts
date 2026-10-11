import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { CalendarRange } from "../../domain/entities/calendar-range";
import type { WorkforceEmployeeSearch } from "../../domain/entities/workforce-employee-search";

export const calendarsPath = "/workforce/calendars";
export const calendarPath = (id: string): string => `${calendarsPath}/${encodeURIComponent(id)}`;
export function calendarWeekFrom(from: string): CalendarRange {
  if (!isCalendarDate(from)) return { from, until: from };
  const end = Math.min(
    Date.parse(`${from}T00:00:00Z`) + 6 * 86_400_000,
    Date.parse("9999-12-31T00:00:00Z"),
  );
  return Object.freeze({ from, until: new Date(end).toISOString().slice(0, 10) });
}
export function calendarRange(
  parameters: URLSearchParams,
  company: CompanyId,
  today: string,
): CalendarRange {
  if (parameters.has("company") && parameters.get("company") !== company)
    return calendarWeekFrom(today);
  const from = parameters.get("from") ?? today;
  return Object.freeze({ from, until: parameters.get("until") ?? calendarWeekFrom(from).until });
}
export function calendarParameters(company: CompanyId, range: CalendarRange): URLSearchParams {
  return new URLSearchParams({ company, from: range.from, until: range.until });
}
export function workforceEmployeeSearch(
  parameters: URLSearchParams,
  company: CompanyId,
  today: string,
): WorkforceEmployeeSearch {
  if (parameters.has("company") && parameters.get("company") !== company)
    return Object.freeze({ asOf: today, query: "", after: null });
  return Object.freeze({
    asOf: parameters.get("asOf") ?? today,
    query: parameters.get("query") ?? "",
    after: parameters.get("after"),
  });
}
export function workforceEmployeeParameters(
  company: CompanyId,
  search: WorkforceEmployeeSearch,
): URLSearchParams {
  const parameters = new URLSearchParams({ company, asOf: search.asOf });
  if (search.query !== "") parameters.set("query", search.query);
  if (search.after !== null) parameters.set("after", search.after);
  return parameters;
}
