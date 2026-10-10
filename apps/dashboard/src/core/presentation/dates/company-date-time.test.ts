import { describe, expect, it } from "vitest";
import { companyDateTimeInput, parseCompanyDateTime } from "./company-date-time";

describe("company datetime input", () => {
  it("uses the explicit company timezone and preserves a stored microsecond instant", () => {
    const original = "2026-10-10T09:12:13.123456Z";
    expect(companyDateTimeInput(original, "Asia/Jakarta")).toBe("2026-10-10T16:12:13");
    expect(parseCompanyDateTime("2026-10-10T16:12:13", "Asia/Jakarta", original)).toBe(original);
    expect(parseCompanyDateTime("2026-10-10T16:13", "Asia/Jakarta", original)).toBe(
      "2026-10-10T09:13:00Z",
    );
  });
  it("rejects nonexistent and ambiguous newly entered local times without changing a retained fold", () => {
    expect(parseCompanyDateTime("2026-03-08T02:30", "America/New_York")).toBeNull();
    expect(parseCompanyDateTime("2026-11-01T01:30", "America/New_York")).toBeNull();
    const original = "2026-11-01T06:30:00.123456Z";
    expect(parseCompanyDateTime("2026-11-01T01:30", "America/New_York", original)).toBe(original);
    expect(parseCompanyDateTime("2026-11-01T02:30", "America/New_York", original)).toBe(
      "2026-11-01T07:30:00Z",
    );
  });
  it("rejects impossible dates, offsets, unbounded input, and invalid zones", () => {
    for (const input of [
      "2026-02-30T10:30",
      "2026-01-01T25:00",
      "2026-10-10T01:00Z",
      "x".repeat(10000),
      "",
    ])
      expect(parseCompanyDateTime(input, "Asia/Jakarta")).toBeNull();
    expect(parseCompanyDateTime("2026-01-01T00:00", "not-a-zone")).toBeNull();
  });
});
