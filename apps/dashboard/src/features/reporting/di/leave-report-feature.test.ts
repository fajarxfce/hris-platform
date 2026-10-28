import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import { leaveStatuses } from "../../leave/domain/entities/leave-request";
import type { LeaveReportQuery } from "../domain/entities/leave-report-request";
import { leaveReportCursorPrefix } from "../domain/policies/leave-report-policy";
import { formatLeaveReportDays } from "../presentation/models/leave-report-view";
import { createReportingFeature } from "./reporting-feature";

const companyId = "77ed1727-6b89-48f9-ae10-f5f3f87c93f0" as CompanyId;
const access = { companyId, permissions: ["reports.read", "leave.read"] };
const from = "2024-01-01",
  until = "2024-12-31";
const totals = { requests: 3, employments: 2, days: "2.5" };
const metadata = {
  companyId,
  from,
  until,
  evaluatedAt: "2026-10-01T00:00:00Z",
  definitionVersion: "leave.v1",
};
const dto = {
  ...metadata,
  totals,
  statuses: leaveStatuses.map((status) => ({
    status,
    counts:
      status === "PENDING"
        ? { requests: 1, employments: 1, days: "0.5" }
        : status === "APPROVED"
          ? { requests: 2, employments: 2, days: "2" }
          : { requests: 0, employments: 0, days: "0" },
  })),
};
const query: LeaveReportQuery = { from, until, status: "PENDING", after: null };
const row = {
  id: "ffffffff-ffff-4fff-8fff-ffffffffffff",
  employeeId: "eeaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  employeeNumber: "E1",
  employeeName: "Example",
  typeCode: "AL",
  typeName: "Annual",
  status: "PENDING",
  submittedAt: "2023-12-01T00:00:00.000001Z",
  version: 0,
  days: "0.5",
  requestDays: "2",
};
const signal = () => new AbortController().signal;

describe("leave report boundaries", () => {
  it("requires both source and report grants and an annual date window before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createReportingFeature(
      { request },
      {
        downloadBinary: async () => {
          throw new Error("Unexpected file download");
        },
      },
    );
    for (const permissions of [
      [],
      ["reports.read"],
      ["leave.read"],
      ["reports.read", "leave.team.read", "leave.self.manage"],
    ]) {
      expect(
        await feature.loadLeave.execute({ companyId, permissions }, from, until, signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(
        await feature.loadLeaveRequests.execute({ companyId, permissions }, query, signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    }
    for (const [start, end] of [
      [from, "2025-01-01"],
      ["2026-02-29", "2026-03-01"],
      [until, from],
      ["1899-12-31", "1900-01-01"],
      ["2201-01-01", "2201-01-01"],
      ["", until],
    ]) {
      expect(
        await feature.loadLeave.execute(access, start ?? "", end ?? "", signal()),
      ).toMatchObject({
        ok: false,
        failure: { code: "invalid_report_range", parameters: { maximumDays: "366" } },
      });
    }
    expect(
      await feature.loadLeaveRequests.execute(access, { ...query, after: "unscoped" }, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
    expect(request).not.toHaveBeenCalled();
  });

  it("retains distinct employment counts and exact decimal quantities while rejecting incoherent data", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(dto);
    const load = createReportingFeature(
      { request },
      {
        downloadBinary: async () => {
          throw new Error("Unexpected file download");
        },
      },
    ).loadLeave;
    const result = await load.execute(access, from, until, signal());
    expect(result).toEqual({ ok: true, value: dto });
    if (!result.ok) throw new Error("Fixture failed");
    expect(Object.isFrozen(result.value.statuses[0]?.counts)).toBe(true);
    for (const changes of [
      { companyId: row.employeeId },
      { from: "2024-01-02" },
      { definitionVersion: "future" },
      { statuses: dto.statuses.slice(1) },
      { statuses: dto.statuses.toReversed() },
      { statuses: [dto.statuses[0], dto.statuses[0], ...dto.statuses.slice(2)] },
      ...[
        { requests: 4 },
        { employments: 1 },
        { days: "2.500" },
        { days: "3" },
        { days: "-0.5" },
        { days: "4611686018427387904" },
        { requests: Number.MAX_SAFE_INTEGER + 1 },
      ].map((change) => ({ totals: { ...totals, ...change } })),
    ]) {
      request.mockResolvedValueOnce({ ...dto, ...changes });
      expect(await load.execute(access, from, until, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    expect(formatLeaveReportDays("4503599627370496.5", "en")).toBe("4,503,599,627,370,496.5");
    expect(formatLeaveReportDays("4503599627370496.5", "id")).toBe("4.503.599.627.370.496,5");
  });

  it("validates request scope, microsecond order, units and bounded cursor progress", async () => {
    const page = {
      ...metadata,
      status: "PENDING",
      items: [
        row,
        { ...row, id: "11111111-1111-4111-8111-111111111111", submittedAt: "2023-12-01T00:00:00Z" },
      ],
      nextCursor: null,
    };
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(page);
    const load = createReportingFeature(
      { request },
      {
        downloadBinary: async () => {
          throw new Error("Unexpected file download");
        },
      },
    ).loadLeaveRequests;
    expect(await load.execute(access, query, signal())).toEqual({ ok: true, value: page });
    expect(request.mock.lastCall?.[0].path).toContain(
      "/reports/leave/requests?from=2024-01-01&until=2024-12-31&limit=50&status=PENDING",
    );
    for (const changes of [
      { companyId: row.employeeId },
      { status: null },
      { items: page.items.toReversed() },
      { items: [row, row] },
      { items: [{ ...row, days: "2.5" }] },
      { items: [{ ...row, status: "APPROVED" }] },
      { items: [{ ...row, requestDays: "366.5" }] },
      { items: [{ ...row, employeeName: " " }] },
      { nextCursor: "wrong" },
      { items: Array.from({ length: 51 }, () => row) },
    ]) {
      request.mockResolvedValueOnce({ ...page, ...changes });
      expect(await load.execute(access, query, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    const items = Array.from({ length: 50 }, (_, index) => ({
      ...row,
      id: `00000000-0000-4000-8000-${String(1000 - index).padStart(12, "0")}`,
    }));
    const last = items.at(-1);
    const nextCursor = `${leaveReportCursorPrefix(companyId, query)}${last?.submittedAt}|${last?.id}`;
    request.mockResolvedValueOnce({ ...page, items, nextCursor });
    expect(await load.execute(access, query, signal())).toMatchObject({
      ok: true,
      value: { nextCursor },
    });
    request.mockResolvedValueOnce({
      ...page,
      items: [{ ...row, id: "00000000-0000-4000-8000-000000000950" }],
      nextCursor: null,
    });
    expect(await load.execute(access, { ...query, after: nextCursor }, signal())).toMatchObject({
      ok: true,
    });
    request.mockResolvedValueOnce({ ...page, items: [last], nextCursor: null });
    expect(await load.execute(access, { ...query, after: nextCursor }, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });

  it("sanitizes failures and rejects late cancellation without implicit retries", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
      new HttpResponseError(403, {
        code: "company_module_disabled",
        parameters: { module: "REPORTING" },
        detail: "Private report diagnostic",
      }),
    );
    const feature = createReportingFeature(
      { request },
      {
        downloadBinary: async () => {
          throw new Error("Unexpected file download");
        },
      },
    );
    const failed = await feature.loadLeave.execute(access, from, until, signal());
    expect(failed).toMatchObject({ ok: false, failure: { code: "company_module_disabled" } });
    expect(JSON.stringify(failed)).not.toContain("Private report diagnostic");
    expect(request).toHaveBeenCalledTimes(1);
    const owner = new AbortController();
    request.mockImplementationOnce(async () => {
      owner.abort();
      return { ...metadata, status: query.status, items: [row], nextCursor: null };
    });
    await expect(
      feature.loadLeaveRequests.execute(access, query, owner.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(() => feature.loadLeave.execute(access, from, until, owner.signal)).toThrow();
  });
});
