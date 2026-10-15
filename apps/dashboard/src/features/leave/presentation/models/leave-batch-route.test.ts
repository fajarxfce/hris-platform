import { describe, expect, it } from "vitest";
import { batchCompany, batchId } from "../../di/leave-batch-fixture";
import { initialLeaveBatchPeriod, leaveBatchRoute } from "./leave-batch-route";

describe("company-owned leave processing routes", () => {
  it("replaces foreign company and result/directory cursors", () => {
    const route = leaveBatchRoute(
      batchCompany,
      new URLSearchParams({
        company: "another",
        after: batchId,
        resultAfter: "20",
        kind: "YEAR_CLOSE",
      }),
      `/${batchId}`,
    );
    expect(route.foreignCompany).toBe(true);
    expect(route.listTo).toBe(`/leave/batches?company=${batchCompany}`);
    expect(route.after).toBeNull();
    expect(route.resultAfter).toBeNull();
  });
  it("keeps separate list and employee-result cursors", () => {
    const route = leaveBatchRoute(
      batchCompany,
      new URLSearchParams({ company: batchCompany, after: batchId, resultAfter: "20" }),
      `/${batchId}`,
    );
    expect(route.listTo).toContain(`after=${batchId}`);
    expect(route.listTo).not.toContain("resultAfter");
    expect(route.current).toContain("resultAfter=20");
    expect(route.newYearCloseTo).toContain("kind=YEAR_CLOSE");
    expect(route.newYearCloseTo).not.toContain("resultAfter");
  });
  it("canonicalizes missing scope and refuses an unknown explicit operation", () => {
    expect(leaveBatchRoute(batchCompany, new URLSearchParams(), "/new")).toMatchObject({
      canonical: false,
      kind: "ACCRUAL",
    });
    expect(
      leaveBatchRoute(
        batchCompany,
        new URLSearchParams({ company: batchCompany, kind: "delete" }),
        "/new",
      ).invalidKind,
    ).toBe(true);
  });
  it.each([
    ["2026-10-15", "ACCRUAL", "2026-09"],
    ["2026-01-01", "ACCRUAL", "2025-12"],
    ["2026-10-15", "YEAR_CLOSE", "2025-12"],
  ] as const)("uses a previous closed selection for %s %s", (today, kind, expected) => {
    expect(initialLeaveBatchPeriod(today, kind)).toBe(expected);
  });
});
