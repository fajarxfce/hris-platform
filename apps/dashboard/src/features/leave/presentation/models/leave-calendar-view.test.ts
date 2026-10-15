import { describe, expect, it } from "vitest";
import { toLeaveCalendar } from "../../data/mappers/leave-calendar-mapper";
import { calendarCompany, calendarWire } from "../../di/leave-calendar-fixture";
import { adjacentLeaveCalendarMonth, leaveCalendarView } from "./leave-calendar-view";

const calendar = toLeaveCalendar(calendarWire, calendarCompany, "COMPANY", null);
describe("calendar date layout", () => {
  it("shows employees instead of adding two half-day requests as two people", () => {
    const view = leaveCalendarView(calendar, "2026-10-05", "en");
    const day = view.weeks.flatMap((week) => week.days).find((cell) => cell.date === "2026-10-05");
    expect(day).toMatchObject({ count: "1", selected: true });
    expect(view.counts).toBe("1 employee · 2 requests");
    expect(day?.label).toContain("Pending: 1");
    expect(day?.label).toContain("Approved: 1");
  });
  it("uses complete ISO weeks and leaves outside-month cells inactive", () => {
    const view = leaveCalendarView(calendar, null, "en");
    expect(view.weeks).toHaveLength(5);
    expect(view.weeks.every((week) => week.days.length === 7)).toBe(true);
    expect(view.weeks.flatMap((week) => week.days).filter((day) => day.date !== null)).toHaveLength(
      31,
    );
    expect(view.weekdays.map((day) => day.label)).toEqual([
      "Mon",
      "Tue",
      "Wed",
      "Thu",
      "Fri",
      "Sat",
      "Sun",
    ]);
    expect(view.weeks[0]?.days[0]?.date).toBeNull();
  });
  it("retains the work date independently of company and browser timezones", () => {
    const view = leaveCalendarView(
      { ...calendar, timezone: "Pacific/Honolulu" },
      "2026-10-15",
      "en",
    );
    expect(view.selectedTitle).toBe("Thursday, October 15, 2026");
    expect(view.weeks.flatMap((week) => week.days).find((day) => day.today)?.date).toBe(
      "2026-10-15",
    );
  });
  it("uses Indonesian month, date and count labels", () => {
    const view = leaveCalendarView(calendar, "2026-10-05", "id");
    expect(view.title).toBe("Oktober 2026");
    expect(view.counts).toBe("1 karyawan · 2 permintaan");
  });
  it.each([
    ["2024-02", 29],
    ["1900-02", 28],
    ["2200-02", 28],
    ["2026-08", 31],
  ] as const)("bounds %s to %i actual dates", (month, days) => {
    const view = leaveCalendarView({ ...calendar, month, days: [] }, null, "en");
    expect(view.weeks.length).toBeLessThanOrEqual(6);
    expect(view.weeks.flatMap((week) => week.days).filter((day) => day.date !== null)).toHaveLength(
      days,
    );
  });
  it("month navigation crosses years and stops at API boundaries", () => {
    expect(adjacentLeaveCalendarMonth("2026-12", 1)).toBe("2027-01");
    expect(adjacentLeaveCalendarMonth("2026-01", -1)).toBe("2025-12");
    expect(adjacentLeaveCalendarMonth("1900-01", -1)).toBeNull();
    expect(adjacentLeaveCalendarMonth("2200-12", 1)).toBeNull();
    expect(adjacentLeaveCalendarMonth("invalid", 1)).toBeNull();
  });
});
