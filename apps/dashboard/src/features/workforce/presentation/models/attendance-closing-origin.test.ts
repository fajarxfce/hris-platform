import { expect, it } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { attendanceClosingOrigin, withAttendanceClosingOrigin } from "./attendance-closing-origin";

const company = "77ed1727-6b89-48f9-ae10-f5f3f87c93f0" as CompanyId;
const cursor = `attendance-closings.v1|${company}|2026-09|aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa`;
it("retains exact report month/window/page through a selected revision and original snapshot", () => {
  const params = new URLSearchParams({
    company,
    reportFrom: "2026-01",
    reportUntil: "2026-12",
    reportAfter: cursor,
  });
  const origin = attendanceClosingOrigin(params, company, "2026-09");
  expect(origin).not.toBeNull();
  expect(new URL(`https://example.invalid${origin?.to}`).searchParams.get("after")).toBe(cursor);
  const link = withAttendanceClosingOrigin(
    `/workforce/periods/2026-09/employees/employee?company=${company}`,
    origin,
  );
  expect(
    attendanceClosingOrigin(new URLSearchParams(link.split("?")[1]), company, "2026-09"),
  ).toEqual(origin);
});
it("drops foreign, malformed and incompatible report origins", () => {
  const base = { company, reportFrom: "2026-01", reportUntil: "2026-12", reportAfter: cursor };
  for (const changes of [
    { company: "other" },
    { reportFrom: "1999-12" },
    { reportUntil: "2027-01" },
    { reportFrom: "2026-10" },
    { reportUntil: "2026-08" },
    { reportAfter: cursor.replace("2026-09", "2026-08") },
    { reportAfter: "//attacker.invalid" },
  ])
    expect(
      attendanceClosingOrigin(new URLSearchParams({ ...base, ...changes }), company, "2026-09"),
    ).toBeNull();
});
