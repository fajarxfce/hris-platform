import { describe, expect, it } from "vitest";
import { emptyOvertimePlanValues, parseOvertimePlanValues } from "./overtime-plan-values";

describe("overtime plan editor values", () => {
  it("requires explicit time input and converts the company's zone", () => {
    expect(parseOvertimePlanValues(emptyOvertimePlanValues(), "Asia/Jakarta")).toBeNull();
    expect(
      parseOvertimePlanValues(
        {
          startsAt: "2026-10-13T22:00",
          endsAt: "2026-10-14T01:00",
          breakMinutes: "15",
          reason: "Work",
        },
        "Asia/Jakarta",
      ),
    ).toEqual({
      startsAt: "2026-10-13T15:00:00Z",
      endsAt: "2026-10-13T18:00:00Z",
      breakMinutes: 15,
    });
  });
  it.each(["2026-03-08T02:30", "2026-11-01T01:30"])(
    "does not guess a nonexistent or ambiguous new time: %s",
    (startsAt) => {
      expect(
        parseOvertimePlanValues(
          { startsAt, endsAt: "2026-11-01T03:00", breakMinutes: "0", reason: "Work" },
          "America/New_York",
        ),
      ).toBeNull();
    },
  );
  it.each(["", "1.5", "1e2", "-1", "1000"])(
    "rejects malformed break input %s before duration policy",
    (breakMinutes) => {
      expect(
        parseOvertimePlanValues(
          {
            startsAt: "2026-10-13T18:00",
            endsAt: "2026-10-13T20:00",
            breakMinutes,
            reason: "Work",
          },
          "Asia/Jakarta",
        ),
      ).toBeNull();
    },
  );
});
