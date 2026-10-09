import { expect, it } from "vitest";
import { companyDate } from "./company-date";

it("uses company-local calendar dates at midnight and year boundaries", () => {
  expect(companyDate("Asia/Jakarta", new Date("2026-12-31T17:01:00Z"))).toBe("2027-01-01");
  expect(companyDate("America/New_York", new Date("2026-01-01T01:00:00Z"))).toBe("2025-12-31");
});
