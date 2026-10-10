import { Temporal } from "@js-temporal/polyfill";

/** Native datetime-local fields display the company zone, never the browser's implicit zone. */
export function companyDateTimeInput(instant: string, timezone: string): string {
  return Temporal.Instant.from(instant)
    .toZonedDateTimeISO(timezone)
    .toPlainDateTime()
    .toString({ smallestUnit: "second" });
}

/** Preserve an unchanged stored instant; reject clock gaps and ambiguous newly entered times. */
export function parseCompanyDateTime(
  value: string,
  timezone: string,
  original: string | null = null,
): string | null {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?$/u.test(value)) return null;
  try {
    const local = Temporal.PlainDateTime.from(value, { overflow: "reject" });
    if (
      original &&
      local.toString({ smallestUnit: "second" }) === companyDateTimeInput(original, timezone)
    )
      return original;
    return local.toZonedDateTime(timezone, { disambiguation: "reject" }).toInstant().toString();
  } catch (error) {
    if (error instanceof RangeError) return null;
    throw error;
  }
}
