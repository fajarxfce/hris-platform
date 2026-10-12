import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { OvertimeDetailsDto } from "../data/models/overtime-details-dto";
import type { OvertimePageDto } from "../data/models/overtime-request-dto";
import type { OvertimeSearchInput } from "../domain/entities/overtime-search";
import {
  company,
  employeeId,
  identifier,
  overtimeAccess,
  overtimeDate,
  overtimeDetailsDtoFixture,
  overtimeDtoFixture,
  overtimeId,
  overtimeSearch,
} from "./overtime-fixture";
import { createWorkforceFeature } from "./workforce-feature";

function fixture() {
  const details = overtimeDetailsDtoFixture();
  const page: OvertimePageDto = {
    items: Array.from({ length: 50 }, (_, index) => ({
      ...overtimeDtoFixture(),
      id: identifier(701 + index),
      employeeId: identifier(1000 + index),
    })),
    nextCursor: identifier(750),
  };
  const request = vi
    .fn<HttpClient["request"]>()
    .mockImplementation(async (input) => (input.path.includes(`${overtimeId}?`) ? details : page));
  const feature = createWorkforceFeature({ request });
  return {
    request,
    page,
    details,
    feature,
    list: (input: OvertimeSearchInput = overtimeSearch, signal = new AbortController().signal) =>
      feature.loadOvertimeRequests.execute(overtimeAccess, input, signal),
    get: (after: string | null = null, signal = new AbortController().signal) =>
      feature.loadOvertimeRequest.execute(overtimeAccess, overtimeId, after, signal),
  };
}

describe("overtime directory boundary", () => {
  it("maps immutable company/query references and bounded exclusive pages", async () => {
    const f = fixture();
    const result = await f.list();
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value).toMatchObject({
      companyId: company,
      search: overtimeSearch,
      nextCursor: identifier(750),
    });
    expect(result.value.items).toHaveLength(50);
    expect(Object.isFrozen(result.value)).toBe(true);
    expect(Object.isFrozen(result.value.search)).toBe(true);
    expect(Object.isFrozen(result.value.items)).toBe(true);
    const item = result.value.items[0],
      raw = f.page.items[0];
    if (!item || !raw) throw new Error("Missing overtime row");
    expect(Object.isFrozen(item)).toBe(true);
    expect(Object.isFrozen(item.actual)).toBe(true);
    expect(Object.isFrozen(item.schedule)).toBe(true);
    raw.employeeName = "Changed transport data";
    raw.requested.workedMinutes = 1;
    expect(item.employeeName).toBe("Avery Tan");
    expect(item.requested.workedMinutes).toBe(180);
    const call = f.request.mock.calls[0]?.[0];
    if (!call) throw new Error("Missing overtime request");
    const url = new URL(call.path, "https://example.invalid");
    expect(url.pathname).toBe(`/api/v1/companies/${company}/workforce/overtime`);
    expect(Object.fromEntries(url.searchParams)).toEqual({
      from: overtimeDate,
      until: overtimeDate,
      limit: "50",
    });
    f.page.items = [{ ...overtimeDtoFixture(), id: identifier(751) }];
    f.page.nextCursor = null;
    expect(
      await f.list({
        ...overtimeSearch,
        employeeId: employeeId.toUpperCase(),
        status: "PENDING",
        after: identifier(750).toUpperCase(),
      }),
    ).toMatchObject({ ok: true, value: { items: [{ id: identifier(751) }], nextCursor: null } });
    expect(f.request.mock.calls[1]?.[0].path).toContain(`employeeId=${employeeId}`);
    expect(f.request.mock.calls[1]?.[0].path).toContain(`after=${identifier(750)}`);
  });

  it.each([
    { from: "2026-02-30" },
    { from: "1999-12-31" },
    { until: "2101-01-01" },
    { from: "2026-11-01" },
    { from: "2026-01-01", until: "2026-03-04" },
    { status: "CANCELLED" },
    { status: "" },
    { employeeId: "../other" },
    { after: "" },
    { after: "x".repeat(1000) },
  ])("rejects invalid search before I/O: %j", async (change) => {
    const f = fixture();
    expect(await f.list({ ...overtimeSearch, ...change })).toMatchObject({
      ok: false,
      failure: { code: "invalid_overtime_page" },
    });
    expect(f.request).not.toHaveBeenCalled();
  });

  it("keeps company browsing separate from employee and assigned review access", async () => {
    const f = fixture();
    expect(
      await f.feature.loadOvertimeRequests.execute(
        { ...overtimeAccess, permissions: ["overtime.approve"] },
        overtimeSearch,
        new AbortController().signal,
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    for (const permission of [
      "overtime.self.manage",
      "overtime.team.read",
      "workforce.team.read",
    ]) {
      expect(
        await f.feature.loadOvertimeRequests.execute(
          { ...overtimeAccess, permissions: [permission] },
          overtimeSearch,
          new AbortController().signal,
        ),
      ).toMatchObject({ ok: false, failure: { code: "employee_scope_required" } });
    }
    expect(f.request).not.toHaveBeenCalled();
    f.page.items = [overtimeDtoFixture()];
    f.page.nextCursor = null;
    expect(
      await f.feature.loadOvertimeRequests.execute(
        { ...overtimeAccess, permissions: ["overtime.self.manage"] },
        { ...overtimeSearch, employeeId },
        new AbortController().signal,
      ),
    ).toMatchObject({ ok: true });
    expect(
      await f.feature.loadOvertimeRequest.execute(
        { ...overtimeAccess, permissions: ["overtime.approve"] },
        overtimeId,
        null,
        new AbortController().signal,
      ),
    ).toMatchObject({ ok: true });
  });

  it("accepts an empty directory for an inclusive 62-day query", async () => {
    const f = fixture();
    f.page.items = [];
    f.page.nextCursor = null;
    expect(
      await f.list({ ...overtimeSearch, from: "2026-01-01", until: "2026-03-03" }),
    ).toMatchObject({ ok: true, value: { items: [], nextCursor: null } });
  });

  it.each([
    "duplicate ID",
    "wrong order",
    "oversized page",
    "short continuation",
    "bad cursor",
    "outside dates",
    "employee mismatch",
    "status mismatch",
    "exclusive cursor",
  ] as const)("rejects %s in the data boundary", async (fault) => {
    const f = fixture(),
      first = f.page.items[0],
      second = f.page.items[1];
    if (!first || !second) throw new Error("Missing overtime rows");
    let search: OvertimeSearchInput = overtimeSearch;
    if (fault === "duplicate ID") second.id = first.id.toUpperCase();
    if (fault === "wrong order") f.page.items.reverse();
    if (fault === "oversized page") f.page.items.push(overtimeDtoFixture());
    if (fault === "short continuation") f.page.items.pop();
    if (fault === "bad cursor") f.page.nextCursor = identifier(999);
    if (fault === "outside dates") search = { ...search, until: "2026-10-11", from: "2026-10-01" };
    if (fault === "employee mismatch") search = { ...search, employeeId };
    if (fault === "status mismatch") search = { ...search, status: "APPROVED" };
    if (fault === "exclusive cursor") search = { ...search, after: first.id };
    expect(await f.list(search)).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });

  it("retains the validated query while the caller changes its input during I/O", async () => {
    const f = fixture(),
      input = { ...overtimeSearch };
    f.request.mockImplementation(async () => {
      input.from = "2026-11-01";
      return f.page;
    });
    expect(await f.list(input)).toMatchObject({ ok: true, value: { search: overtimeSearch } });
  });
});

describe("overtime detail boundary", () => {
  it.each(["PLANNED", "PENDING", "APPROVED", "REJECTED", "WITHDRAWN"] as const)(
    "maps %s time facts and immutable history",
    async (status) => {
      const f = fixture();
      f.request.mockResolvedValue(overtimeDetailsDtoFixture(status));
      const result = await f.get();
      expect(result.ok).toBe(true);
      if (!result.ok) return;
      expect(result.value.request).toMatchObject({
        id: overtimeId,
        companyId: company,
        status,
        requested: { workedMinutes: 180 },
        approvedMinutes: status === "APPROVED" ? 135 : 0,
      });
      expect(Object.isFrozen(result.value)).toBe(true);
      expect(Object.isFrozen(result.value.actions)).toBe(true);
      expect(Object.isFrozen(result.value.history.items)).toBe(true);
      if (result.value.approval) {
        expect(Object.isFrozen(result.value.approval.stages)).toBe(true);
        expect(Object.isFrozen(result.value.approval.stages[0])).toBe(true);
      }
    },
  );

  it("keeps a planned withdrawal without manufacturing actual time or approval", async () => {
    const f = fixture(),
      dto = overtimeDetailsDtoFixture("PLANNED");
    dto.request = {
      ...dto.request,
      status: "WITHDRAWN",
      version: 1,
      decidedAt: "2026-10-12T11:00:00Z",
    };
    const planned = dto.history.items[0];
    if (!planned) throw new Error("Missing planned history");
    dto.history.items.unshift({
      ...planned,
      revision: 1,
      kind: "WITHDRAWN",
      status: "WITHDRAWN",
      reason: "No longer required",
    });
    dto.actions = [];
    f.request.mockResolvedValue(dto);
    expect(await f.get()).toMatchObject({
      ok: true,
      value: { approval: null, request: { actual: null, status: "WITHDRAWN" } },
    });
  });

  it("advances history with exclusive revisions and preserves the current snapshot", async () => {
    const f = fixture();
    f.request.mockResolvedValue(overtimeDetailsDtoFixture("PENDING", 52));
    expect(await f.get()).toMatchObject({
      ok: true,
      value: { request: { version: 52 }, history: { nextCursor: "3" } },
    });
    f.request.mockResolvedValue(overtimeDetailsDtoFixture("PENDING", 52, "3"));
    expect(await f.get("3")).toMatchObject({
      ok: true,
      value: {
        request: { version: 52 },
        history: { items: [{ revision: 2 }, { revision: 1 }, { revision: 0 }], nextCursor: null },
      },
    });
    expect(f.request.mock.calls[1]?.[0].path).toContain("historyAfter=3");
    f.request.mockResolvedValue(overtimeDetailsDtoFixture("PENDING", 52, "0"));
    expect(await f.get("0")).toMatchObject({
      ok: true,
      value: { history: { items: [], nextCursor: null } },
    });
  });

  it.each(["", "-1", "01", "1e2", "1.1", "9007199254740992", "9".repeat(100)])(
    "rejects history cursor %s before I/O",
    async (after) => {
      const f = fixture();
      expect(await f.get(after)).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
      expect(f.request).not.toHaveBeenCalled();
    },
  );

  it("rejects a path fragment instead of an overtime ID", async () => {
    const f = fixture();
    expect(
      await f.feature.loadOvertimeRequest.execute(
        overtimeAccess,
        "../other",
        null,
        new AbortController().signal,
      ),
    ).toMatchObject({ ok: false, failure: { code: "overtime_request_not_found" } });
    expect(f.request).not.toHaveBeenCalled();
  });

  it.each([
    [
      "wrong request ID",
      (d: OvertimeDetailsDto) => {
        d.request.id = identifier(999);
      },
    ],
    [
      "wrong date",
      (d: OvertimeDetailsDto) => {
        d.request.workDate = "2026-02-30";
      },
    ],
    [
      "invalid timezone",
      (d: OvertimeDetailsDto) => {
        d.request.timezone = "Not/AZone";
      },
    ],
    [
      "missing name",
      (d: OvertimeDetailsDto) => {
        d.request.employeeName = " ";
      },
    ],
    [
      "blank reason",
      (d: OvertimeDetailsDto) => {
        d.request.reason = " ";
      },
    ],
    [
      "invalid timestamp",
      (d: OvertimeDetailsDto) => {
        d.request.createdAt = "2026-02-30T00:00:00Z";
      },
    ],
    [
      "fractional minute",
      (d: OvertimeDetailsDto) => {
        d.request.requested.startsAt = "2026-10-12T07:00:00.000000001Z";
      },
    ],
    [
      "invalid duration",
      (d: OvertimeDetailsDto) => {
        d.request.requested.workedMinutes = 179;
      },
    ],
    [
      "overlong interval",
      (d: OvertimeDetailsDto) => {
        d.request.requested.endsAt = "2026-10-12T20:00:00Z";
      },
    ],
    [
      "missing actual",
      (d: OvertimeDetailsDto) => {
        d.request.actual = null;
      },
    ],
    [
      "missing submitter",
      (d: OvertimeDetailsDto) => {
        d.request.submittedBy = null;
      },
    ],
    [
      "unapproved minutes",
      (d: OvertimeDetailsDto) => {
        d.request.approvedMinutes = 135;
      },
    ],
    [
      "early decision",
      (d: OvertimeDetailsDto) => {
        d.request.decidedAt = d.request.createdAt;
      },
    ],
    [
      "excess revision",
      (d: OvertimeDetailsDto) => {
        d.request.version = 1000;
      },
    ],
    [
      "missing approval",
      (d: OvertimeDetailsDto) => {
        d.approval = null;
      },
    ],
    [
      "duplicate actions",
      (d: OvertimeDetailsDto) => {
        d.actions = ["APPROVE", "APPROVE"];
      },
    ],
    [
      "unavailable action",
      (d: OvertimeDetailsDto) => {
        d.actions = ["SUBMIT_ACTUAL"];
      },
    ],
    [
      "empty latest history",
      (d: OvertimeDetailsDto) => {
        d.history.items = [];
      },
    ],
    [
      "reordered history",
      (d: OvertimeDetailsDto) => {
        d.history.items.reverse();
      },
    ],
    [
      "short continued history",
      (d: OvertimeDetailsDto) => {
        d.history.nextCursor = "1";
      },
    ],
  ] as const)("rejects %s without exposing transport errors", async (_name, change) => {
    const f = fixture();
    change(f.details);
    expect(await f.get()).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it("rejects conflicting workflow and history references", async () => {
    for (const change of [
      (d: OvertimeDetailsDto) => {
        if (d.approval) d.approval.id = identifier(999);
      },
      (d: OvertimeDetailsDto) => {
        if (d.approval) d.approval.stages = [[identifier(22), identifier(22).toUpperCase()]];
      },
      (d: OvertimeDetailsDto) => {
        if (d.approval) d.approval.currentStep = 2;
      },
      (d: OvertimeDetailsDto) => {
        if (d.approval) d.approval.status = "APPROVED";
      },
      (d: OvertimeDetailsDto) => {
        const first = d.history.items[0];
        if (first) first.status = "APPROVED";
      },
      (d: OvertimeDetailsDto) => {
        const first = d.history.items[0];
        if (first?.actual) first.actual.breakMinutes = 14;
      },
    ]) {
      const f = fixture();
      change(f.details);
      expect(await f.get()).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });
});

describe("overtime cancellation and safe failures", () => {
  it.each(["list", "get"] as const)("preserves API assurance failures in %s", async (operation) => {
    const f = fixture();
    f.request.mockRejectedValue(
      new HttpResponseError(403, { code: "mfa_required", detail: "private transport content" }),
    );
    expect(await f[operation]()).toEqual({
      ok: false,
      failure: { code: "mfa_required", fields: {}, parameters: {} },
    });
  });
  it.each(["list", "get"] as const)(
    "propagates cancellation before and after %s acquisition",
    async (operation) => {
      const f = fixture(),
        controller = new AbortController();
      controller.abort();
      await expect(
        operation === "list"
          ? f.list(overtimeSearch, controller.signal)
          : f.get(null, controller.signal),
      ).rejects.toMatchObject({ name: "AbortError" });
      expect(f.request).not.toHaveBeenCalled();
      for (const fails of [false, true]) {
        const pending = new AbortController();
        f.request.mockImplementation(async () => {
          pending.abort();
          if (fails) throw new TypeError("Late I/O failure");
          return operation === "list" ? f.page : f.details;
        });
        await expect(
          operation === "list"
            ? f.list(overtimeSearch, pending.signal)
            : f.get(null, pending.signal),
        ).rejects.toMatchObject({ name: "AbortError" });
      }
    },
  );
});
