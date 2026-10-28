import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import {
  closingAccess as access,
  closingCompany as company,
  closingEmployeePage as page,
  closingReport as report,
} from "./attendance-closing-report-fixture";
import { createReportingFeature } from "./reporting-feature";

const signal = () => new AbortController().signal;
const query = { month: "2026-09", after: null };
describe("attendance closing report boundary", () => {
  it("requires company source/report authority and rejects incompatible ranges and cursors before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(report),
      feature = createReportingFeature(
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
      ["workforce.read"],
      ["reports.read", "workforce.team.read", "attendance.correct", "workforce.close"],
    ]) {
      expect(
        await feature.loadAttendanceClosings.execute(
          { companyId: company, permissions },
          report.from,
          report.until,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(
        await feature.loadAttendanceClosingEmployees.execute(
          { companyId: company, permissions },
          query,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    }
    for (const [from, until] of [
      ["1999-12", "2000-01"],
      ["2100-12", "2101-01"],
      ["2026-01", "2027-01"],
      ["2026-10", "2026-09"],
      ["2026-09-01", "2026-09"],
    ] as const)
      expect(
        await feature.loadAttendanceClosings.execute(access, from, until, signal()),
      ).toMatchObject({
        ok: false,
        failure: { code: "invalid_report_month_range", parameters: { minimumYear: "2000" } },
      });
    for (const after of [
      "bad",
      `attendance-closings.v1|${company}|2026-10|aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa`,
      "x".repeat(111),
    ])
      expect(
        await feature.loadAttendanceClosingEmployees.execute(access, { ...query, after }, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
    expect(request).not.toHaveBeenCalled();
  });
  it("maps original/current totals and exact selected revisions into detached scoped results", async () => {
    const original = structuredClone(report),
      employees = structuredClone(page);
    const request = vi
        .fn<HttpClient["request"]>()
        .mockResolvedValueOnce(original)
        .mockResolvedValueOnce(employees),
      feature = createReportingFeature(
        { request },
        {
          downloadBinary: async () => {
            throw new Error("Unexpected file download");
          },
        },
      );
    const result = await feature.loadAttendanceClosings.execute(
      access,
      report.from,
      report.until,
      signal(),
    );
    const rows = await feature.loadAttendanceClosingEmployees.execute(access, query, signal());
    expect(result).toEqual({ ok: true, value: report });
    expect(rows).toEqual({ ok: true, value: page });
    expect(request.mock.calls[1]?.[0].path).toBe(
      `/api/v1/companies/${company}/reports/attendance-closings/employees?month=2026-09&limit=50`,
    );
    expect(result.ok && Object.isFrozen(result.value.totals.current)).toBe(true);
    expect(rows.ok && Object.isFrozen(rows.value.items[0]?.adjustment)).toBe(true);
  });
  it("rejects malformed historical references, cross-company data and unreconciled totals", async () => {
    const invalid = [
      { ...report, companyId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa" },
      { ...report, totals: { ...report.totals, days: 31 } },
      { ...report, totals: { ...report.totals, scheduledMinutes: 999999 } },
      { ...report, months: [] },
      { ...report, months: [{ ...report.months[0], closing: null }] },
      {
        ...report,
        months: [
          { ...report.months[0], closing: { ...report.months[0]?.closing, timezone: "invalid" } },
        ],
      },
      {
        ...report,
        totals: { ...report.totals, current: { ...report.totals.current, acceptedMinutes: 3000 } },
      },
    ];
    for (const dto of invalid) {
      const load = createReportingFeature(
        {
          request: vi.fn().mockResolvedValue(dto),
        },
        {
          downloadBinary: async () => {
            throw new Error("Unexpected file download");
          },
        },
      ).loadAttendanceClosings;
      expect((await load.execute(access, report.from, report.until, signal())).ok).toBe(false);
    }
    const row = page.items[0];
    if (!row) throw new Error("fixture");
    for (const dto of [
      { ...page, items: [{ ...row, adjustment: null }] },
      { ...page, items: [row, row] },
      {
        ...page,
        items: [{ ...row, adjustment: { ...row.adjustment, recordedAt: "2026-09-01T00:00:00Z" } }],
      },
      { ...page, nextCursor: `attendance-closings.v1|${company}|2026-09|${row.employeeId}` },
    ])
      expect(
        (
          await createReportingFeature(
            {
              request: vi.fn().mockResolvedValue(dto),
            },
            {
              downloadBinary: async () => {
                throw new Error("Unexpected file download");
              },
            },
          ).loadAttendanceClosingEmployees.execute(access, query, signal())
        ).ok,
      ).toBe(false);
  });
  it("validates full fifty-row continuations against their last retained employment", async () => {
    const base = page.items[0];
    if (!base) throw new Error("fixture");
    const items = Array.from({ length: 50 }, (_, index) => ({
      ...base,
      employeeId: `10000000-0000-4000-8000-${String(index + 1).padStart(12, "0")}`,
    }));
    const after = `attendance-closings.v1|${company}|2026-09|${items.at(-1)?.employeeId}`;
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce({ ...page, items, nextCursor: after })
      .mockResolvedValueOnce({ ...page, items: [], nextCursor: null });
    const load = createReportingFeature(
      { request },
      {
        downloadBinary: async () => {
          throw new Error("Unexpected file download");
        },
      },
    ).loadAttendanceClosingEmployees;
    expect(await load.execute(access, query, signal())).toMatchObject({
      ok: true,
      value: { nextCursor: after },
    });
    expect(await load.execute(access, { ...query, after }, signal())).toMatchObject({
      ok: true,
      value: { items: [], nextCursor: null },
    });
    expect(
      new URL(`https://example.invalid${request.mock.calls[1]?.[0].path}`).searchParams.get(
        "after",
      ),
    ).toBe(after);
  });
});
