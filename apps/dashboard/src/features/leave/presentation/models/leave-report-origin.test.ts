import { expect, it } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { leaveReportOrigin, leaveReportReturn } from "./leave-report-origin";
import { leaveParameters } from "./leave-route";

const company = "77ed1727-6b89-48f9-ae10-f5f3f87c93f0" as CompanyId;
const input = () =>
  new URLSearchParams({
    company,
    reportFrom: "2026-01-01",
    reportUntil: "2026-12-31",
    reportStatus: "PENDING",
  });
it("retains a scoped report filter through request actions and history", () => {
  const origin = leaveReportOrigin(input(), company);
  expect(origin).toEqual({
    from: "2026-01-01",
    until: "2026-12-31",
    status: "PENDING",
    after: null,
  });
  if (!origin) throw new Error("Missing origin");
  const next = leaveParameters(
    company,
    { employeeId: null, status: null, after: null },
    "history",
    null,
    origin,
  );
  expect(leaveReportOrigin(next, company)).toEqual(origin);
  expect(leaveReportReturn(company, origin)).toBe(
    `/reports/leave/requests?company=${company}&from=2026-01-01&until=2026-12-31&status=PENDING`,
  );
  const after = `1|${company}|2026-01-01|2026-12-31|PENDING|2025-12-01T00:00:00Z|11111111-1111-4111-8111-111111111111`;
  const paged = input();
  paged.set("reportAfter", after);
  const retained = leaveReportOrigin(paged, company);
  expect(retained?.after).toBe(after);
  if (!retained) throw new Error("Missing paged origin");
  expect(
    new URL(leaveReportReturn(company, retained), "https://hris.example.test").searchParams.get(
      "after",
    ),
  ).toBe(after);
  paged.set("reportStatus", "APPROVED");
  expect(leaveReportOrigin(paged, company)).toBeNull();
});
it("drops foreign or malformed origins instead of forwarding an arbitrary return target", () => {
  for (const [key, value] of [
    ["company", "ea6c898f-b127-4b7e-b548-616c44e8c122"],
    ["reportFrom", "2025-01-01"],
    ["reportUntil", "not-a-date"],
    ["reportStatus", "unknown"],
    ["reportAfter", "https://example.invalid/"],
  ]) {
    const params = input();
    params.set(key ?? "", value ?? "");
    expect(leaveReportOrigin(params, company)).toBeNull();
  }
});
