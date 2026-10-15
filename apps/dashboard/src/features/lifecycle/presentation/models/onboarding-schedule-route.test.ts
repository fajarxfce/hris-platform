import { expect, it } from "vitest";
import {
  onboardingCompany as company,
  onboardingEmployee as employee,
} from "../../di/onboarding-schedule-fixture";
import { onboardingScheduleRoute } from "./onboarding-schedule-route";

it("preserves employee directory scope and escapes identifiers through nested actions", () => {
  const route = onboardingScheduleRoute(
    company,
    employee,
    new URLSearchParams({
      company,
      asOf: "2026-10-02",
      query: "Joining & teams",
      after: "cursor/part",
    }),
    "2026-10-01",
    "/schedule/cancel",
  );
  expect(route.foreignCompany).toBe(false);
  expect(route.canonical).toBe(true);
  expect(route.current).toContain("/schedule/cancel?");
  const params = new URLSearchParams(route.query);
  expect(params.get("asOf")).toBe("2026-10-02");
  expect(params.get("query")).toBe("Joining & teams");
  expect(params.get("after")).toBe("cursor/part");
  expect(route.employeeTo).toBe(`/people/employees/${employee}?${route.query}`);
  expect(
    onboardingScheduleRoute(company, "../unsafe", new URLSearchParams(), "2026-10-01").path,
  ).toContain("..%2Funsafe");
});
it("company replacement discards foreign dates and filters before directory navigation", () => {
  const route = onboardingScheduleRoute(
    company,
    employee,
    new URLSearchParams({
      company: "other",
      asOf: "1999-01-01",
      query: "PRIVATE",
      after: "private-cursor",
    }),
    "2026-10-01",
  );
  expect(route.foreignCompany).toBe(true);
  expect(route.directory).toBe(`/people/employees?company=${company}&asOf=2026-10-01`);
  expect(route.query).not.toContain("PRIVATE");
  const missing = onboardingScheduleRoute(
    company,
    employee,
    new URLSearchParams(),
    "2026-10-01",
    "/new",
  );
  expect(missing.canonical).toBe(false);
  expect(missing.current).toBe(`${missing.path}/new?company=${company}&asOf=2026-10-01`);
});
