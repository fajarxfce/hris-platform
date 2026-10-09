import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import { createReportingFeature } from "./reporting-feature";

const companyId = "77ed1727-6b89-48f9-ae10-f5f3f87c93f0" as CompanyId;
const access = { companyId, permissions: ["reports.read", "people.read"] };
const asOf = "2026-10-01";
const dto = {
  companyId,
  asOf,
  evaluatedAt: "2026-10-01T00:00:00Z",
  definitionVersion: "headcount.v1",
  employments: 3,
  persons: 2,
  active: 2,
  probation: 0,
  suspended: 1,
  permanent: 2,
  fixedTerm: 1,
};
const signal = () => new AbortController().signal;

describe("headcount feature boundaries", () => {
  it("requires both permissions and valid bounded calendar dates before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createReportingFeature({ request });
    for (const permissions of [
      [],
      ["reports.read"],
      ["people.read"],
      ["reports.read", "people.team.read"],
    ]) {
      expect(
        await feature.loadHeadcount.execute({ companyId, permissions }, asOf, signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    }
    for (const date of ["", "2101-01-01", "1899-12-31", "2026-02-29", "2026-13-01", "2026-1-01"]) {
      expect(await feature.loadHeadcount.execute(access, date, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_report_date" },
      });
    }
    expect(request).not.toHaveBeenCalled();
  });

  it("keeps distinct people and validates response scope, definition and reconciled totals", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const feature = createReportingFeature({ request });
    const loaded = await feature.loadHeadcount.execute(access, asOf, signal());
    expect(loaded).toEqual({ ok: true, value: dto });
    if (!loaded.ok) throw new Error("Fixture did not load");
    expect(Object.isFrozen(loaded.value)).toBe(true);
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/reports/headcount?asOf=2026-10-01`,
    );
    for (const changes of [
      { companyId: "b721fda3-c884-498e-b1d9-9c79fc387187" },
      { asOf: "2026-09-01" },
      { definitionVersion: "unknown" },
      { persons: 4 },
      { active: 3 },
      { fixedTerm: 2 },
      { employments: -1 },
      { persons: Number.MAX_SAFE_INTEGER + 1 },
    ]) {
      request.mockResolvedValueOnce({ ...dto, ...changes });
      expect(await feature.loadHeadcount.execute(access, asOf, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });

  it("preserves server failure codes without retries and propagates cancellation", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
      new HttpResponseError(403, {
        code: "company_module_disabled",
        parameters: { module: "REPORTING" },
        detail: "Private operational text",
      }),
    );
    const feature = createReportingFeature({ request });
    const result = await feature.loadHeadcount.execute(access, asOf, signal());
    expect(result).toMatchObject({
      ok: false,
      failure: { code: "company_module_disabled", parameters: { module: "REPORTING" } },
    });
    expect(JSON.stringify(result)).not.toContain("Private operational");
    expect(request).toHaveBeenCalledTimes(1);
    const cancelled = new AbortController();
    cancelled.abort();
    expect(() => feature.loadHeadcount.execute(access, asOf, cancelled.signal)).toThrow();
    expect(request).toHaveBeenCalledTimes(1);
  });
});
