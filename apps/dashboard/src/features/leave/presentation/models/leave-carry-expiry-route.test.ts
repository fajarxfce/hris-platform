import { describe, expect, it } from "vitest";
import { batchCompany, batchEmployee } from "../../di/leave-batch-fixture";
import { carryExpiryId } from "../../di/leave-carry-expiry-fixture";
import { leaveCarryExpiryRoute } from "./leave-carry-expiry-route";

describe("carry expiry navigation scope", () => {
  it("drops every resource cursor when the selected company changes", () => {
    const route = leaveCarryExpiryRoute(
      batchCompany,
      new URLSearchParams({
        company: batchEmployee(99),
        after: carryExpiryId,
        resultAfter: "20",
      }),
      `/${carryExpiryId}`,
    );
    expect(route.foreignCompany).toBe(true);
    expect(route.after).toBeNull();
    expect(route.resultAfter).toBeNull();
    expect(route.listTo).toBe(`/leave/carry-expiries?company=${batchCompany}`);
  });
  it("preserves the originating directory while paging employee results", () => {
    const route = leaveCarryExpiryRoute(
      batchCompany,
      new URLSearchParams({
        company: batchCompany,
        after: carryExpiryId,
        resultAfter: "20",
      }),
      `/${carryExpiryId}/resume`,
    );
    expect(route.listTo).toContain(`after=${carryExpiryId}`);
    expect(route.listTo).not.toContain("resultAfter");
    expect(route.current).toContain(`/${carryExpiryId}/resume?`);
    expect(route.detailQuery).toContain("resultAfter=20");
  });
});
