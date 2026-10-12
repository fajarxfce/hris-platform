import { describe, expect, it } from "vitest";
import { toOvertimeDetails } from "../../data/mappers/overtime-details-mapper";
import { company, overtimeDetailsDtoFixture, overtimeId } from "../../di/overtime-fixture";
import type { OvertimeRequestId } from "../../domain/entities/overtime-request";
import { overtimeActionValues, parseOvertimeActualValues } from "./overtime-actual-values";

const review = toOvertimeDetails(
  overtimeDetailsDtoFixture("PLANNED"),
  company,
  overtimeId as OvertimeRequestId,
  null,
);
describe("overtime local input", () => {
  it("edits the request zone regardless of the browser zone and preserves unchanged instants", () => {
    const values = overtimeActionValues(review);
    expect(values).toEqual({
      startsAt: "2026-10-12T14:00",
      endsAt: "2026-10-12T17:00",
      breakMinutes: "0",
      reason: "",
    });
    expect(parseOvertimeActualValues(values, review)).toEqual({
      startsAt: "2026-10-12T07:00:00Z",
      endsAt: "2026-10-12T10:00:00Z",
      breakMinutes: 0,
    });
    expect(
      parseOvertimeActualValues(
        { ...values, startsAt: "2026-10-12T14:15", endsAt: "2026-10-12T16:45", breakMinutes: "15" },
        review,
      ),
    ).toEqual({
      startsAt: "2026-10-12T07:15:00Z",
      endsAt: "2026-10-12T09:45:00Z",
      breakMinutes: 15,
    });
  });
  it.each(["", "-1", "1.5", "1e2", "10000"])(
    "does not coerce invalid break text %s",
    (breakMinutes) => {
      expect(
        parseOvertimeActualValues({ ...overtimeActionValues(review), breakMinutes }, review),
      ).toBeNull();
    },
  );
  it("rejects newly entered clock gaps and ambiguity while preserving a stored fold", () => {
    const zoned = {
      ...review,
      request: {
        ...review.request,
        timezone: "America/New_York",
        requested: {
          ...review.request.requested,
          startsAt: "2026-11-01T05:00:00Z",
          endsAt: "2026-11-01T07:00:00Z",
        },
      },
    };
    const values = overtimeActionValues(zoned);
    expect(parseOvertimeActualValues(values, zoned)?.startsAt).toBe("2026-11-01T05:00:00Z");
    expect(
      parseOvertimeActualValues({ ...values, startsAt: "2026-11-01T01:30" }, zoned),
    ).toBeNull();
    expect(
      parseOvertimeActualValues({ ...values, startsAt: "2026-03-08T02:30" }, zoned),
    ).toBeNull();
  });
  it("keeps an overnight interval's dates and does not retain a denied review", () => {
    const overnight = {
      ...review,
      request: {
        ...review.request,
        requested: {
          ...review.request.requested,
          startsAt: "2026-10-12T15:00:00Z",
          endsAt: "2026-10-12T20:00:00Z",
        },
      },
    };
    expect(overtimeActionValues(overnight)).toMatchObject({
      startsAt: "2026-10-12T22:00",
      endsAt: "2026-10-13T03:00",
    });
    expect(overtimeActionValues(null)).toEqual({
      startsAt: "",
      endsAt: "",
      breakMinutes: "0",
      reason: "",
    });
  });
});
