import { describe, expect, it } from "vitest";
import { activeNavigationDestination, filterNavigationMenu } from "./navigation-menu";

const sections = [
  { id: "home", label: null, items: [{ to: "/", label: "Overview", icon: null }] },
  {
    id: "requests",
    label: "Requests",
    items: [
      { to: "/approvals", label: "Approvals", icon: null },
      { to: "/approvals/templates", label: "Templates", icon: null },
    ],
  },
  {
    id: "workforce",
    label: "Time and attendance",
    items: [{ to: "/workforce/attendance", label: "Attendance", icon: null }],
  },
  {
    id: "reports",
    label: "Reporting",
    items: [
      {
        to: "/reports?companies=longer-than-the-export-destination",
        label: "Catalogue",
        icon: null,
      },
      { to: "/reports/exports?company=one#history", label: "Exports", icon: null },
    ],
  },
];

describe("workspace menu projection", () => {
  it("selects one most specific visible ancestor on a complete path segment", () => {
    expect(activeNavigationDestination(sections, "/approvals/templates/new")).toBe(
      "/approvals/templates",
    );
    expect(
      activeNavigationDestination(sections, "/workforce/attendance/employee/days/2026-10-12"),
    ).toBe("/workforce/attendance");
    expect(activeNavigationDestination(sections, "/approvals-extra")).toBeNull();
    expect(activeNavigationDestination(sections, "/")).toBe("/");
    expect(activeNavigationDestination([], "/workforce/attendance")).toBeNull();
    for (const path of ["/reports/exports", "/reports/exports/new", "/reports/exports/one"])
      expect(activeNavigationDestination(sections, path)).toBe(
        "/reports/exports?company=one#history",
      );
    expect(activeNavigationDestination(sections, "/reports/exports-extra")).toBe(
      "/reports?companies=longer-than-the-export-destination",
    );
  });
  it("filters labels and sections without introducing destinations or mutating the source", () => {
    expect(filterNavigationMenu(sections, "  ATTENDANCE ").map((section) => section.id)).toEqual([
      "workforce",
    ]);
    expect(filterNavigationMenu(sections, "requests")[0]?.items).toHaveLength(2);
    expect(filterNavigationMenu(sections, "payroll")).toEqual([]);
    expect(filterNavigationMenu(sections, " ")).toBe(sections);
    expect(sections[1]?.items).toHaveLength(2);
  });
});
