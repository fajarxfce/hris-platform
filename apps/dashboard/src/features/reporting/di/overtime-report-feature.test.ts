import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import { failureMessage } from "../../../core/presentation/i18n/failure-message";
import { overtimeStatuses } from "../../workforce/domain/entities/overtime-request";
import { createReportingFeature } from "./reporting-feature";

const companyId = "77ed1727-6b89-48f9-ae10-f5f3f87c93f0" as CompanyId;
const access = { companyId, permissions: ["reports.read", "overtime.read"] };
const from = "2026-08-31",
  until = "2026-10-01";
const counts = {
  requests: 1,
  employments: 1,
  actualRequests: 1,
  requestedMinutes: 180,
  actualMinutes: 135,
  approvedMinutes: 135,
};
const totals = {
  requests: 3,
  employments: 1,
  actualRequests: 3,
  requestedMinutes: 540,
  actualMinutes: 405,
  approvedMinutes: 405,
};
const dto = {
  companyId,
  from,
  until,
  evaluatedAt: "2026-10-01T00:00:00Z",
  definitionVersion: "overtime.v1",
  totals,
  months: [
    { month: "2026-08", from, until: "2026-08-31", counts },
    { month: "2026-09", from: "2026-09-01", until: "2026-09-30", counts },
    { month: "2026-10", from: "2026-10-01", until, counts },
  ],
};
const signal = () => new AbortController().signal;

describe("overtime report boundaries", () => {
  it("requires company source and report authority and validates annual dates and status before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const load = createReportingFeature(
      { request },
      {
        downloadBinary: async () => {
          throw new Error("Unexpected file download");
        },
      },
    ).loadOvertime;
    for (const permissions of [
      [],
      ["reports.read"],
      ["overtime.read"],
      ["reports.read", "overtime.team.read"],
      ["reports.read", "overtime.self.manage"],
      ["reports.read", "overtime.approve"],
    ])
      expect(
        await load.execute({ companyId, permissions }, from, until, null, signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const [a, b] of [
      ["2026-09-02", "2026-09-01"],
      ["2024-01-01", "2025-01-01"],
      ["2026-02-29", until],
      ["1999-12-31", "2000-01-01"],
      ["2100-12-31", "2101-01-01"],
      ["", until],
    ]) {
      const result = await load.execute(access, a ?? "", b ?? "", null, signal());
      expect(result).toMatchObject({
        ok: false,
        failure: {
          code: "invalid_report_range",
          parameters: { maximumDays: "366", minimumYear: "2000", maximumYear: "2100" },
        },
      });
      if (!result.ok) {
        expect(failureMessage(result.failure, "en")).toContain("between 2000 and 2100");
        expect(failureMessage(result.failure, "id")).toContain("antara tahun 2000 dan 2100");
      }
    }
    for (const status of ["", "ALL", "CANCELLED", "approved"])
      expect(await load.execute(access, from, until, status, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_report_status" },
      });
    expect(request).not.toHaveBeenCalled();
    for (const permission of ["overtime.read", "overtime.manage", "workforce.read"])
      expect(
        (
          await load.execute(
            { companyId, permissions: ["reports.read", permission] },
            from,
            until,
            null,
            signal(),
          )
        ).ok,
      ).toBe(true);
  });

  it("requires matching scope, complete ordered clipped months and coherent exact integer totals", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const load = createReportingFeature(
      { request },
      {
        downloadBinary: async () => {
          throw new Error("Unexpected file download");
        },
      },
    ).loadOvertime;
    const result = await load.execute(access, from, until, null, signal());
    expect(result).toEqual({ ok: true, value: { ...dto, status: null } });
    if (!result.ok) throw new Error("Fixture did not load");
    expect(Object.isFrozen(result.value.months)).toBe(true);
    expect(Object.isFrozen(result.value.months[0]?.counts)).toBe(true);
    expect(result.value.totals.employments).toBe(1);
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/reports/overtime?from=${from}&until=${until}`,
    );
    for (const changes of [
      { companyId: "b721fda3-c884-498e-b1d9-9c79fc387187" },
      { from: "2026-08-30" },
      { until: "2026-10-02" },
      { status: "APPROVED" },
      { definitionVersion: "unknown" },
      { months: dto.months.slice(1) },
      { months: dto.months.toReversed() },
      { months: [dto.months[0], dto.months[0], dto.months[2]] },
      { months: Array.from({ length: 14 }, () => dto.months[0]) },
      { months: [{ ...dto.months[0], from: "2026-08-01" }, ...dto.months.slice(1)] },
      { months: [dto.months[0], { ...dto.months[1], until: "2026-09-29" }, dto.months[2]] },
      ...[
        { requests: -1 },
        { employments: 0 },
        { employments: 4 },
        { actualRequests: 4 },
        { requestedMinutes: 400 },
        { actualMinutes: 406 },
        { approvedMinutes: 406 },
        { approvedMinutes: 1.5 },
        { requestedMinutes: Number.MAX_SAFE_INTEGER + 1 },
      ].map((change) => ({ totals: { ...totals, ...change } })),
      {
        months: [
          { ...dto.months[0], counts: { ...counts, actualRequests: 0 } },
          ...dto.months.slice(1),
        ],
      },
    ]) {
      request.mockResolvedValueOnce({ ...dto, ...changes });
      expect(await load.execute(access, from, until, null, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });

  it("accepts every current status but refuses quantities inconsistent with that status", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const load = createReportingFeature(
      { request },
      {
        downloadBinary: async () => {
          throw new Error("Unexpected file download");
        },
      },
    ).loadOvertime;
    for (const status of overtimeStatuses) {
      const selected = {
        ...counts,
        actualRequests: status === "PLANNED" ? 0 : 1,
        actualMinutes: status === "PLANNED" ? 0 : 135,
        approvedMinutes: status === "APPROVED" ? 135 : 0,
      };
      const scoped = {
        ...dto,
        from,
        until: from,
        status,
        totals: selected,
        months: [{ month: "2026-08", from, until: from, counts: selected }],
      };
      request.mockResolvedValueOnce(scoped);
      expect((await load.execute(access, from, from, status, signal())).ok).toBe(true);
      expect(request.mock.lastCall?.[0].path).toContain(`status=${status}`);
      const broken = { ...selected, approvedMinutes: status === "APPROVED" ? 0 : 1 };
      request.mockResolvedValueOnce({
        ...scoped,
        totals: broken,
        months: [{ ...scoped.months[0], counts: broken }],
      });
      expect(await load.execute(access, from, from, status, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });

  it("admits a 366-day leap window spanning 13 months with complete zero buckets", async () => {
    const zero = {
      requests: 0,
      employments: 0,
      actualRequests: 0,
      requestedMinutes: 0,
      actualMinutes: 0,
      approvedMinutes: 0,
    };
    const start = "2024-01-31",
      end = "2025-01-30";
    const months = Array.from({ length: 13 }, (_, i) => {
      const month = new Date(Date.UTC(2024, i, 1)).toISOString().slice(0, 7);
      return {
        month,
        from: i === 0 ? start : `${month}-01`,
        until: i === 12 ? end : new Date(Date.UTC(2024, i + 1, 0)).toISOString().slice(0, 10),
        counts: zero,
      };
    });
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ ...dto, from: start, until: end, totals: zero, months });
    expect(
      (
        await createReportingFeature(
          { request },
          {
            downloadBinary: async () => {
              throw new Error("Unexpected file download");
            },
          },
        ).loadOvertime.execute(access, start, end, null, signal())
      ).ok,
    ).toBe(true);
  });

  it("preserves sanitized failures, cancels late acquisitions and performs no automatic retry", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
      new HttpResponseError(403, {
        code: "company_module_disabled",
        parameters: { module: "REPORTING" },
        detail: "PRIVATE diagnostic",
      }),
    );
    const load = createReportingFeature(
      { request },
      {
        downloadBinary: async () => {
          throw new Error("Unexpected file download");
        },
      },
    ).loadOvertime;
    const result = await load.execute(access, from, until, null, signal());
    expect(result).toMatchObject({ ok: false, failure: { code: "company_module_disabled" } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    expect(request).toHaveBeenCalledTimes(1);
    const owner = new AbortController();
    request.mockImplementationOnce(async () => {
      owner.abort();
      return dto;
    });
    await expect(load.execute(access, from, until, null, owner.signal)).rejects.toMatchObject({
      name: "AbortError",
    });
    request.mockClear();
    expect(() => load.execute(access, from, until, null, owner.signal)).toThrow();
    expect(request).not.toHaveBeenCalled();
  });
});
