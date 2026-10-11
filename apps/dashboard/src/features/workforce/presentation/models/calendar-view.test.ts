import { describe, expect, it } from "vitest";
import { calendarFixture, identifier, shiftFixture } from "../../di/calendar-fixture";
import { calendarWeekFrom } from "./calendar-route";
import { calendarShiftOptions, shiftChoiceKey } from "./calendar-shift-options";
import { calendarView } from "./calendar-view";

describe("work-calendar presentation values", () => {
  it("preserves calendar dates and explicitly labels overnight shifts", () => {
    const view = calendarView(calendarFixture(), "en");
    expect(view.days[0]?.cells).toContain("Oct 12, 2026");
    expect(view.days[0]?.cells).toContain("22:00–06:00 (next day)");
    expect(view.days[0]?.cells).toContain("Asia/Jakarta");
    expect(calendarView(calendarFixture(), "id").pattern[0]?.cells[0]).toBe("Senin");
    expect(calendarWeekFrom("9999-12-30")).toEqual({ from: "9999-12-30", until: "9999-12-31" });
    expect(calendarWeekFrom("2026-02-30")).toEqual({ from: "2026-02-30", until: "2026-02-30" });
  });
  it("retains selected revisions while replacing catalog pages, without upgrading them silently", () => {
    const original = calendarFixture().assignmentAtStart?.days.MONDAY;
    if (!original) throw new Error("Expected assigned shift");
    const first = calendarShiftOptions(
      [shiftFixture(1, 1), { ...shiftFixture(2), active: false }],
      [original],
      "en",
    );
    expect(first.map((option) => option.key)).toEqual([`${identifier()}:1`, `${identifier()}:0`]);
    expect(shiftChoiceKey(original)).toBe(`${identifier()}:0`);
    const next = calendarShiftOptions([shiftFixture(3)], [original], "en");
    expect(next.map((option) => option.key)).toEqual([`${identifier(3)}:0`, `${identifier()}:0`]);
    expect(next).toHaveLength(2);
    expect(original.revision).toBe(0);
  });
});
