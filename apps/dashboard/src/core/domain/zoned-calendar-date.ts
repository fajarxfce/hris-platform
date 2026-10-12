/** A supplied instant's calendar date in an explicit zone; this does not read the clock. */
export function zonedCalendarDate(timezone: string, at: Date): string {
  const parts = new Intl.DateTimeFormat("en-US", {
    calendar: "iso8601",
    timeZone: timezone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(at);
  const values = new Map(parts.map((part) => [part.type, part.value]));
  return `${values.get("year")}-${values.get("month")}-${values.get("day")}`;
}
