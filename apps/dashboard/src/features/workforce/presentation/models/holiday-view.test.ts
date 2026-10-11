import { describe, expect, it } from "vitest";
import { company, holidayFixture } from "../../di/holiday-fixture";
import { holidayParameters, holidayRange } from "./holiday-route";
import { holidayDirectoryView, holidayView } from "./holiday-view";

describe("holiday presentation dates", () => {
  it("formats date-only values consistently in English and Indonesian", () => {
    expect(holidayDirectoryView([holidayFixture()], "en")[0]?.cells[0]).toBe("Jan 1, 2026");
    expect(holidayView(holidayFixture(), "id")[0]?.value).toBe("1 Januari 2026");
  });
  it("uses the supplied company year and discards another company's date filters", () => {
    const query = new URLSearchParams({
      company: "other",
      from: "2025-01-01",
      until: "2025-12-31",
    });
    const range = holidayRange(query, company, "2027-01-01");
    expect(range).toEqual({ from: "2027-01-01", until: "2027-12-31" });
    expect(holidayRange(holidayParameters(company, range), company, "2027-12-31")).toEqual(range);
    expect(holidayRange(new URLSearchParams({ from: "invalid" }), company, "2027-01-01").from).toBe(
      "invalid",
    );
  });
});
