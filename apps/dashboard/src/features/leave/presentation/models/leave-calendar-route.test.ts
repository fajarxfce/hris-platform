import { describe, expect, it } from "vitest";
import { calendarCompany } from "../../di/leave-calendar-fixture";
import {
  leaveCalendarOrigin,
  leaveCalendarParameters,
  leaveCalendarRoute,
} from "./leave-calendar-route";

describe("calendar route ownership", () => {
  it("starts company readers in company scope and supervisors in their team", () => {
    expect(leaveCalendarRoute(calendarCompany, ["leave.read"], new URLSearchParams()).scope).toBe(
      "COMPANY",
    );
    expect(
      leaveCalendarRoute(calendarCompany, ["leave.team.read"], new URLSearchParams()).scope,
    ).toBe("TEAM");
  });
  it("drops all resource selections when company changes", () => {
    const route = leaveCalendarRoute(
      calendarCompany,
      ["leave.team.read"],
      new URLSearchParams(
        "company=other&scope=COMPANY&month=2026-09&date=2026-09-20&after=private",
      ),
    );
    expect(route).toMatchObject({
      scope: "TEAM",
      month: null,
      date: null,
      after: null,
      foreignCompany: true,
    });
    expect(route.to).toBe(`/leave/calendar?company=${calendarCompany}&scope=TEAM`);
  });
  it("does not reinterpret an invalid supplied scope as company authority", () => {
    expect(
      leaveCalendarRoute(
        calendarCompany,
        ["leave.read"],
        new URLSearchParams(`company=${calendarCompany}&scope=INVALID`),
      ).scope,
    ).toBeNull();
  });
  it("keeps month and date optional while binding explicit pagination", () => {
    const parameters = leaveCalendarParameters(
      calendarCompany,
      "TEAM",
      null,
      "2026-10-05",
      "cursor",
    );
    expect(parameters.has("month")).toBe(false);
    expect(leaveCalendarRoute(calendarCompany, [], parameters)).toMatchObject({
      canonical: true,
      scope: "TEAM",
      date: "2026-10-05",
      after: "cursor",
    });
  });
});

describe("calendar return context", () => {
  const input = new URLSearchParams({
    company: calendarCompany,
    calendarScope: "TEAM",
    calendarMonth: "2026-10",
    calendarDate: "2026-10-05",
    calendarAfter: "80000000-0000-4000-8000-000000000020",
  });
  it("keeps only a validated same-company date, scope and page", () => {
    input.set("returnTo", "https://example.invalid");
    expect(leaveCalendarOrigin(input, calendarCompany)).toEqual({
      scope: "TEAM",
      month: "2026-10",
      date: "2026-10-05",
      after: input.get("calendarAfter"),
    });
  });
  it.each([
    ["company", "other"],
    ["calendarScope", "ALL"],
    ["calendarMonth", "2026-13"],
    ["calendarDate", "2026-11-05"],
    ["calendarDate", "2026-10-32"],
    ["calendarAfter", "private"],
  ])("drops invalid context at %s=%s", (key, value) => {
    const next = new URLSearchParams(input);
    next.set(key, value);
    expect(leaveCalendarOrigin(next, calendarCompany)).toBeNull();
  });
});
