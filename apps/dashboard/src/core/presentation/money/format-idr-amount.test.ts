import { describe, expect, it } from "vitest";
import { formatIdrAmount } from "./format-idr-amount";

describe("exact IDR presentation", () => {
  it("keeps sub-rupiah refund signs and grouping in both locales", () => {
    expect(formatIdrAmount("-0.01", "en")).toBe("-IDR\u00a00.01");
    expect(formatIdrAmount("-0.01", "id")).toBe("-Rp\u00a00,01");
    expect(formatIdrAmount("-1234.50", "id")).toBe("-Rp\u00a01.234,50");
    expect(formatIdrAmount("0.00", "en")).toBe("IDR\u00a00.00");
  });
  it("keeps all digits of positive and negative amounts beyond binary float precision", () => {
    expect(formatIdrAmount("9007199254740993.01", "en")).toBe("IDR\u00a09,007,199,254,740,993.01");
    expect(formatIdrAmount("-9007199254740993.01", "id")).toBe("-Rp\u00a09.007.199.254.740.993,01");
  });
});
