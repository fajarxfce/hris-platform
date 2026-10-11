import type { CompanyId } from "../../../../core/domain/identifiers";
import type { HolidayRange } from "../../domain/entities/holiday-range";

export const holidaysPath = "/workforce/holidays";
export function holidayRange(
  parameters: URLSearchParams,
  company: CompanyId,
  today: string,
): HolidayRange {
  const year = today.slice(0, 4);
  const initial = Object.freeze({ from: `${year}-01-01`, until: `${year}-12-31` });
  if (parameters.has("company") && parameters.get("company") !== company) return initial;
  return Object.freeze({
    from: parameters.get("from") ?? initial.from,
    until: parameters.get("until") ?? initial.until,
  });
}
export function holidayParameters(company: CompanyId, range: HolidayRange): URLSearchParams {
  return new URLSearchParams({ company, from: range.from, until: range.until });
}
