import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import { createReportingFeature } from "./reporting-feature";

const companyId = "77ed1727-6b89-48f9-ae10-f5f3f87c93f0" as CompanyId;
const access = { companyId, permissions: ["reports.read", "workforce.read"] };
const from = "2026-09-29";
const until = "2026-09-30";
const day = {
  observedDays: 1,
  affectedDays: 1,
  affectedEmployments: 1,
  pendingDays: 1,
  incompleteDays: 1,
  pendingEvents: 2,
};
const totals = {
  observedDays: 2,
  affectedDays: 2,
  affectedEmployments: 1,
  pendingDays: 2,
  incompleteDays: 2,
  pendingEvents: 4,
};
const dto = {
  companyId,
  from,
  until,
  evaluatedAt: "2026-10-01T00:00:00Z",
  definitionVersion: "attendance-exceptions.v1",
  totals,
  days: [
    { date: from, counts: day },
    { date: until, counts: day },
  ],
};
const signal = () => new AbortController().signal;

describe("attendance report boundaries", () => {
  it("requires source/report grants and a bounded valid date window before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const load = createReportingFeature({ request }).loadAttendanceExceptions;
    for (const permissions of [
      [],
      ["reports.read"],
      ["workforce.read"],
      ["reports.read", "workforce.team.read"],
    ])
      expect(await load.execute({ companyId, permissions }, from, until, signal())).toMatchObject({
        ok: false,
        failure: { code: "access_denied" },
      });
    for (const [start, end] of [
      ["2026-09-30", "2026-09-29"],
      ["2026-08-30", "2026-09-30"],
      ["2026-02-29", "2026-03-01"],
      ["", until],
      ["2101-01-01", "2101-01-01"],
    ])
      expect(await load.execute(access, start ?? "", end ?? "", signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_report_range" },
      });
    expect(request).not.toHaveBeenCalled();
  });

  it("keeps distinct employments and rejects wrong scopes, dates or partial/incoherent reports", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const load = createReportingFeature({ request }).loadAttendanceExceptions;
    const result = await load.execute(access, from, until, signal());
    expect(result).toEqual({ ok: true, value: dto });
    if (!result.ok) throw new Error("Fixture did not load");
    expect(Object.isFrozen(result.value.days)).toBe(true);
    expect(Object.isFrozen(result.value.days[0]?.counts)).toBe(true);
    expect(result.value.totals.affectedEmployments).toBe(1);
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/reports/attendance-exceptions?from=${from}&until=${until}`,
    );
    for (const changes of [
      { companyId: "b721fda3-c884-498e-b1d9-9c79fc387187" },
      { from: "2026-09-28" },
      { until: "2026-10-01" },
      { definitionVersion: "unknown" },
      { days: dto.days.slice(0, 1) },
      { days: dto.days.toReversed() },
      { days: [dto.days[0], dto.days[0]] },
      { days: Array.from({ length: 32 }, () => dto.days[0]) },
      ...[
        { affectedEmployments: 0 },
        { pendingDays: 3 },
        { incompleteDays: 3 },
        { pendingEvents: 5 },
        { affectedDays: -1 },
        { pendingEvents: Number.MAX_SAFE_INTEGER + 1 },
        { pendingEvents: 1.5 },
      ].map((change) => ({ totals: { ...totals, ...change } })),
      { days: [{ date: from, counts: { ...day, affectedEmployments: 0 } }, dto.days[1]] },
    ]) {
      request.mockResolvedValueOnce({ ...dto, ...changes });
      expect(await load.execute(access, from, until, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });

  it("preserves sanitized failures, performs no hidden retry and rejects late cancelled I/O", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
      new HttpResponseError(403, {
        code: "company_module_disabled",
        parameters: { module: "REPORTING" },
        detail: "Private diagnostic",
      }),
    );
    const load = createReportingFeature({ request }).loadAttendanceExceptions;
    const failed = await load.execute(access, from, until, signal());
    expect(failed).toMatchObject({ ok: false, failure: { code: "company_module_disabled" } });
    expect(JSON.stringify(failed)).not.toContain("Private diagnostic");
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
