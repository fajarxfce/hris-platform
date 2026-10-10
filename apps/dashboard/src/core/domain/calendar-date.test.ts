import { describe, expect, it } from "vitest";
import { isCalendarDate } from "./calendar-date";

describe("calendar date contract", () => {
  it("accepts real dates independently of browser timezone, including leap centuries", () => {
    for (const value of [
      "0001-01-01",
      "1899-12-31",
      "2000-02-29",
      "2024-02-29",
      "2100-12-31",
      "9999-12-31",
    ])
      expect(isCalendarDate(value)).toBe(true);
  });
  it("rejects normalized overflow, year zero, partial dates and timestamps", () => {
    for (const value of [
      "0000-01-01",
      "1900-02-29",
      "2100-02-29",
      "2026-04-31",
      "2026-13-01",
      "2026-1-01",
      "2026-01",
      "2026-01-01T00:00:00Z",
      " 2026-01-01",
      "10000-01-01",
    ])
      expect(isCalendarDate(value)).toBe(false);
  });
});
