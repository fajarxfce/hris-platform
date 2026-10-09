import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import { createReportingFeature } from "./reporting-feature";

const companyId = "77ed1727-6b89-48f9-ae10-f5f3f87c93f0" as CompanyId;
const access = { companyId, permissions: ["reports.read", "people.read"] };
const asOf = "2026-10-01";
const counts = {
  employments: 3,
  persons: 2,
  active: 2,
  probation: 0,
  suspended: 1,
  permanent: 2,
  fixedTerm: 1,
};
const dto = {
  asOf,
  evaluatedAt: "2026-10-01T00:00:00Z",
  definitionVersion: "headcount.v1",
  totals: counts,
  companies: [{ companyId, counts }],
};
const second = "b721fda3-c884-498e-b1d9-9c79fc387187" as CompanyId;
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
    for (const selection of [
      [],
      [companyId, companyId],
      [companyId, companyId.toUpperCase() as CompanyId],
      ["invalid" as CompanyId],
      Array.from({ length: 33 }, () => crypto.randomUUID() as CompanyId),
    ]) {
      expect(await feature.loadHeadcount.execute(access, asOf, signal(), selection)).toMatchObject({
        ok: false,
        failure: { code: "invalid_report_companies" },
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
    expect(Object.isFrozen(loaded.value.totals)).toBe(true);
    expect(Object.isFrozen(loaded.value.companies)).toBe(true);
    expect(Object.isFrozen(loaded.value.companies[0]?.counts)).toBe(true);
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/reports/headcount?companies=${companyId}&asOf=2026-10-01`,
    );
    for (const changes of [
      { companies: [{ companyId: second, counts }] },
      {
        companies: [
          { companyId, counts },
          { companyId, counts },
        ],
      },
      { companies: [] },
      { asOf: "2026-09-01" },
      { definitionVersion: "unknown" },
      ...[
        { persons: 4 },
        { persons: 0 },
        { active: 3 },
        { fixedTerm: 2 },
        { employments: -1 },
        { persons: Number.MAX_SAFE_INTEGER + 1 },
      ].map((changes) => ({ totals: { ...counts, ...changes } })),
      { companies: [{ companyId, counts: { ...counts, persons: 4 } }] },
    ]) {
      request.mockResolvedValueOnce({ ...dto, ...changes });
      expect(await feature.loadHeadcount.execute(access, asOf, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });

  it("uses server distinct totals, preserves empty companies and validates reconciliation across the complete selection", async () => {
    const empty = "a3e82650-57da-40a5-a930-f78282b93a42" as CompanyId;
    const zero = {
      employments: 0,
      persons: 0,
      active: 0,
      probation: 0,
      suspended: 0,
      permanent: 0,
      fixedTerm: 0,
    };
    const totals = {
      employments: 6,
      persons: 3,
      active: 4,
      probation: 0,
      suspended: 2,
      permanent: 4,
      fixedTerm: 2,
    };
    const group = {
      ...dto,
      totals,
      companies: [
        { companyId, counts },
        { companyId: second, counts },
        { companyId: empty, counts: zero },
      ],
    };
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(group);
    const feature = createReportingFeature({ request });
    const selected = [second, empty, companyId];
    const loaded = await feature.loadHeadcount.execute(access, asOf, signal(), selected);
    expect(loaded).toMatchObject({ ok: true, value: { totals: { employments: 6, persons: 3 } } });
    if (!loaded.ok) throw new Error("Group fixture did not load");
    expect(loaded.value.companies.map((company) => company.companyId)).toEqual(
      [...selected].sort(),
    );
    expect(
      loaded.value.companies.find((company) => company.companyId === empty)?.counts.employments,
    ).toBe(0);
    const url = new URL(request.mock.lastCall?.[0].path ?? "", "https://fixture.test");
    expect(url.searchParams.get("companies")).toBe([...selected].sort().join(","));
    for (const changes of [
      { totals: { ...totals, persons: 1 } },
      { totals: { ...totals, persons: 5 } },
      { companies: group.companies.slice(0, 2) },
      { totals: { ...totals, employments: 7, active: 5, permanent: 5 } },
    ]) {
      request.mockResolvedValueOnce({ ...group, ...changes });
      expect(await feature.loadHeadcount.execute(access, asOf, signal(), selected)).toMatchObject({
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
