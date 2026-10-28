import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import { failureMessage } from "../../../core/presentation/i18n/failure-message";
import { payrollCostReportView } from "../presentation/models/payroll-cost-report-view";
import { createReportingFeature } from "./reporting-feature";

const companyId = "77ed1727-6b89-48f9-ae10-f5f3f87c93f0" as CompanyId;
const access = { companyId, permissions: ["reports.read", "payroll.read"] };
const from = "2026-11",
  until = "2026-12";
const counts = {
  assessments: 1,
  employments: 1,
  revisedAssessments: 1,
  employerCost: "100.03",
  taxableGross: "100.02",
  employeeDeductions: "0.01",
  withheld: "-0.01",
  takeHome: "100.03",
};
const totals = {
  assessments: 2,
  employments: 1,
  revisedAssessments: 2,
  employerCost: "200.06",
  taxableGross: "200.04",
  employeeDeductions: "0.02",
  withheld: "-0.02",
  takeHome: "200.06",
};
const dto = {
  companyId,
  from,
  until,
  evaluatedAt: "2027-01-01T00:00:00Z",
  definitionVersion: "payroll-costs.v1",
  currency: "IDR",
  totals,
  months: [
    { month: from, counts },
    { month: until, counts },
  ],
};
const signal = () => new AbortController().signal;

describe("published payroll cost boundaries", () => {
  it("requires both company payroll and report grants and bounds earnings months before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const load = createReportingFeature({ request }).loadPayrollCosts;
    for (const permissions of [
      [],
      ["reports.read"],
      ["payroll.read"],
      [
        "reports.read",
        "payroll.self.read",
        "payroll.compensation.manage",
        "payroll.policy.manage",
        "payroll.amend",
        "payroll.pay",
      ],
    ])
      expect(await load.execute({ companyId, permissions }, from, until, signal())).toMatchObject({
        ok: false,
        failure: { code: "access_denied" },
      });
    for (const [a, b] of [
      ["2023-12", "2024-01"],
      ["2100-12", "2101-01"],
      ["2026-01", "2027-01"],
      [until, from],
      ["2026-11-01", until],
      ["2026-00", until],
      [from, "2026-13"],
      ["", until],
    ]) {
      const result = await load.execute(access, a ?? "", b ?? "", signal());
      expect(result).toMatchObject({
        ok: false,
        failure: {
          code: "invalid_report_month_range",
          parameters: { maximumMonths: "12", minimumYear: "2024", maximumYear: "2100" },
        },
      });
      if (!result.ok) {
        expect(failureMessage(result.failure, "en")).toContain("twelve earnings months");
        expect(failureMessage(result.failure, "id")).toContain("dua belas bulan penghasilan");
      }
    }
    expect(request).not.toHaveBeenCalled();
  });

  it("reconciles signed cents and distinct employments, detaches inputs and exposes month navigation", async () => {
    const transport = structuredClone(dto);
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(transport);
    const result = await createReportingFeature({ request }).loadPayrollCosts.execute(
      access,
      from,
      until,
      signal(),
    );
    expect(result).toEqual({ ok: true, value: dto });
    if (!result.ok) throw new Error("Fixture did not load");
    expect(Object.isFrozen(result.value.months)).toBe(true);
    expect(Object.isFrozen(result.value.months[0]?.counts)).toBe(true);
    transport.totals.employerCost = "999.00";
    expect(result.value.totals.employerCost).toBe("200.06");
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/reports/payroll-costs?from=${from}&until=${until}`,
    );
    const view = payrollCostReportView(result.value, "id");
    expect(view.metrics.find((row) => row.key === "withheld")?.value).toBe("-Rp\u00a00,02");
    expect(view.rows[1]).toMatchObject({ id: until, actionDisabled: false });
  });

  it("rejects wrong scopes, definitions, missing or unordered months and irreconcilable money/counts", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const load = createReportingFeature({ request }).loadPayrollCosts;
    const changes = [
      { companyId: "b721fda3-c884-498e-b1d9-9c79fc387187" },
      { from: "2026-10" },
      { until: "2027-01" },
      { definitionVersion: "unknown" },
      { currency: "USD" },
      { evaluatedAt: "invalid" },
      { months: dto.months.slice(1) },
      { months: dto.months.toReversed() },
      { months: [dto.months[0], dto.months[0]] },
      { months: Array.from({ length: 13 }, () => dto.months[0]) },
      ...[
        { assessments: -1 },
        { employments: 0 },
        { employments: 3 },
        { revisedAssessments: 3 },
        { assessments: Number.MAX_SAFE_INTEGER + 1 },
        { revisedAssessments: 1.5 },
        { employerCost: "200.05" },
        { taxableGross: "201.00" },
        { employeeDeductions: "201.00" },
        { withheld: "-2400000000000.01" },
        { withheld: "201.00" },
        { takeHome: "999.00" },
        { employerCost: "100000000000.01" },
        { employerCost: "-1.00" },
        { takeHome: "-0.01" },
        { withheld: "0.001" },
        { employerCost: 200.06 },
        { employerCost: "2e2" },
        { taxableGross: "200.040" },
        { takeHome: "0200.06" },
        { withheld: "9".repeat(40) },
      ].map((value) => ({ totals: { ...totals, ...value } })),
      { months: [{ month: from, counts: { ...counts, assessments: 2 } }, dto.months[1]] },
    ];
    for (const change of changes) {
      request.mockResolvedValueOnce({ ...dto, ...change });
      expect(
        await load.execute(access, from, until, signal()),
        JSON.stringify(change),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });

  it("keeps large exact sums and negative cents while permitting twelve zero months across a year boundary", async () => {
    const large = {
      assessments: 200000,
      employments: 200000,
      revisedAssessments: 1,
      employerCost: "9007199254740993.01",
      taxableGross: "9007199254740993.00",
      employeeDeductions: "0.00",
      withheld: "-0.01",
      takeHome: "9007199254740993.02",
    };
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({
      ...dto,
      until: from,
      totals: large,
      months: [{ month: from, counts: large }],
    });
    const load = createReportingFeature({ request }).loadPayrollCosts;
    expect(await load.execute(access, from, from, signal())).toMatchObject({
      ok: true,
      value: { totals: large },
    });
    const zero = {
      assessments: 0,
      employments: 0,
      revisedAssessments: 0,
      employerCost: "0.00",
      taxableGross: "0.00",
      employeeDeductions: "0.00",
      withheld: "0.00",
      takeHome: "0.00",
    };
    const months = Array.from({ length: 12 }, (_, i) => ({
      month: new Date(Date.UTC(2026, 6 + i, 1)).toISOString().slice(0, 7),
      counts: zero,
    }));
    request.mockResolvedValueOnce({
      ...dto,
      from: "2026-07",
      until: "2027-06",
      totals: zero,
      months,
    });
    const result = await load.execute(access, "2026-07", "2027-06", signal());
    expect(result.ok).toBe(true);
    if (result.ok)
      expect(
        payrollCostReportView(result.value, "en").rows.every((row) => row.actionDisabled),
      ).toBe(true);
  });

  it("sanitizes failures, preserves cancellation and performs only explicit retries", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
      new HttpResponseError(403, {
        code: "company_module_disabled",
        parameters: { module: "REPORTING" },
        detail: "PRIVATE DETAIL",
      }),
    );
    const load = createReportingFeature({ request }).loadPayrollCosts;
    const result = await load.execute(access, from, until, signal());
    expect(result).toMatchObject({ ok: false, failure: { code: "company_module_disabled" } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    expect(request).toHaveBeenCalledTimes(1);
    const owner = new AbortController();
    request.mockImplementationOnce(async () => {
      owner.abort();
      return dto;
    });
    await expect(load.execute(access, from, until, owner.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
    request.mockClear();
    expect(() => load.execute(access, from, until, owner.signal)).toThrow();
    expect(request).not.toHaveBeenCalled();
  });
});
