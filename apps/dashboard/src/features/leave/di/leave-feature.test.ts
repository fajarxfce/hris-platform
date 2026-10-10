import { describe, expect, it, vi } from "vitest";
import {
  leaveId,
  leaveRecord,
  leaveSummary,
  leaveWithHistory,
} from "../../../../tests/fixtures/leave";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import type { LeaveRequestDetailsDto } from "../data/models/leave-request-details-dto";
import type { LeaveRequestQuery } from "../domain/entities/leave-request";
import { createLeaveFeature } from "./leave-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["leave.read"] };
const query: LeaveRequestQuery = { employeeId: null, status: null, after: null };
const signal = () => new AbortController().signal;
function firstFixtureItem<T>(items: readonly T[]): T {
  const item = items[0];
  if (item === undefined) throw new Error("Missing fixture item");
  return item;
}

describe("leave request boundary", () => {
  it("requires company or employee scope before listing and rejects malformed filters locally", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLeaveFeature({ request });
    expect(
      await feature.loadRequests.execute({ ...access, permissions: [] }, query, signal()),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await feature.loadRequests.execute(
        { ...access, permissions: ["leave.team.read"] },
        query,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "employee_scope_required" } });
    for (const invalid of [
      { ...query, after: "../foreign" },
      { ...query, employeeId: "bad" },
      { ...query, status: "UNKNOWN" },
    ]) {
      expect(
        await feature.loadRequests.execute(access, invalid as LeaveRequestQuery, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_leave_query" } });
    }
    expect(await feature.loadRequest.execute(access, "../other", null, signal())).toMatchObject({
      ok: false,
      failure: { code: "leave_request_not_found" },
    });
    expect(
      await feature.loadRequest.execute(access, leaveId(), "9007199254740992", signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
    expect(request).not.toHaveBeenCalled();
  });
  it("uses bounded employee/status pages ordered by submission time rather than identifier", async () => {
    const raw = [leaveRecord(0, 1), leaveRecord(0, 2)];
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ items: raw.map(leaveSummary), nextCursor: null });
    const feature = createLeaveFeature({ request });
    const result = await feature.loadRequests.execute(access, query, signal());
    expect(result).toMatchObject({
      ok: true,
      value: {
        items: [
          { id: raw[0]?.id, companyId },
          { id: raw[1]?.id, companyId },
        ],
      },
    });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/leave/requests?limit=20`,
    );
    request.mockResolvedValue({ items: [leaveSummary(firstFixtureItem(raw))], nextCursor: null });
    expect(
      await feature.loadRequests.execute(
        { ...access, permissions: ["leave.team.read"] },
        {
          employeeId: firstFixtureItem(raw).employeeId.toUpperCase(),
          status: "PENDING",
          after: null,
        },
        signal(),
      ),
    ).toMatchObject({ ok: true });
    expect(request.mock.lastCall?.[0].path).toContain(
      `employeeId=${raw[0]?.employeeId}&status=PENDING`,
    );
  });
  it("acquires exact details without inventing client ownership and detaches nested evidence", async () => {
    const raw = leaveRecord();
    raw.approval.requesterId = null;
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
    const result = await createLeaveFeature({ request }).loadRequest.execute(
      { ...access, permissions: [] },
      raw.id.toUpperCase(),
      null,
      signal(),
    );
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    firstFixtureItem(raw.days).chargedMinutes = 100;
    firstFixtureItem(raw.approval.stages).splice(0);
    raw.policy.allowedContracts.splice(0);
    raw.availableActions.splice(0);
    expect(result.value.days[0]?.chargedMinutes).toBe(225);
    expect(result.value.approval.requesterId).toBeNull();
    expect(result.value.approval.stages[0]).toHaveLength(1);
    expect(result.value.policy.allowedContracts).toHaveLength(2);
    expect(result.value.availableActions).toEqual(["DECIDE"]);
    expect(Object.isFrozen(result.value.history.items[0])).toBe(true);
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/leave/requests/${raw.id}?historyLimit=20`,
    );
  });
  it("keeps current request metadata separate from the selected immutable history page", async () => {
    const raw = leaveWithHistory();
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({
      ...raw,
      history: { items: raw.history.items.slice(0, 20), nextCursor: "4" },
    });
    const feature = createLeaveFeature({ request });
    expect(await feature.loadRequest.execute(access, raw.id, null, signal())).toMatchObject({
      ok: true,
      value: { version: 23, history: { nextCursor: "4" } },
    });
    request.mockResolvedValue({
      ...raw,
      history: { items: raw.history.items.slice(20), nextCursor: null },
    });
    expect(await feature.loadRequest.execute(access, raw.id, "4", signal())).toMatchObject({
      ok: true,
      value: {
        version: 23,
        history: { items: [{ version: 3 }, { version: 2 }, { version: 1 }, { version: 0 }] },
      },
    });
    expect(request.mock.lastCall?.[0].path).toContain("historyLimit=20&historyAfter=4");
  });
  it.each<[string, (record: LeaveRequestDetailsDto) => void]>([
    [
      "identity",
      (record) => {
        record.id = leaveId(1);
      },
    ],
    [
      "calendar",
      (record) => {
        firstFixtureItem(record.days).workDate = "2026-02-30";
      },
    ],
    [
      "duration",
      (record) => {
        firstFixtureItem(record.days).endsAt = firstFixtureItem(record.days).startsAt;
      },
    ],
    [
      "quantity",
      (record) => {
        record.chargedDays = "1";
      },
    ],
    [
      "assignees",
      (record) => {
        record.approval.stages = [[record.authorId, record.authorId]];
      },
    ],
    [
      "history",
      (record) => {
        firstFixtureItem(record.history.items).version = 2;
      },
    ],
    [
      "actions",
      (record) => {
        record.availableActions.push("DECIDE");
      },
    ],
    [
      "cancellation",
      (record) => {
        record.status = "CANCELLATION_PENDING";
      },
    ],
  ])("rejects inconsistent %s without exposing partial details", async (_name, mutate) => {
    const raw = leaveRecord();
    mutate(raw);
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
    expect(
      await createLeaveFeature({ request }).loadRequest.execute(access, leaveId(), null, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("rejects repeated list cursors, foreign employee projections and out-of-order pages", async () => {
    const first = leaveSummary(leaveRecord());
    const second = leaveSummary(leaveRecord(0, 2));
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLeaveFeature({ request });
    for (const items of [
      [first, first],
      [second, first],
    ]) {
      request.mockResolvedValue({ items, nextCursor: null });
      expect(await feature.loadRequests.execute(access, query, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    request.mockResolvedValue({ items: [first], nextCursor: first.id });
    expect(
      await feature.loadRequests.execute(access, { ...query, after: first.id }, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    request.mockResolvedValue({ items: [first], nextCursor: null });
    expect(
      await feature.loadRequests.execute(
        access,
        { ...query, employeeId: second.employeeId },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("retains safe failures and propagates cancellation before delivery or after acquisition", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockRejectedValue(
        new HttpResponseError(404, { code: "leave_request_not_found", detail: "PRIVATE" }),
      );
    const feature = createLeaveFeature({ request });
    expect(await feature.loadRequest.execute(access, leaveId(), null, signal())).toMatchObject({
      ok: false,
      failure: { code: "leave_request_not_found" },
    });
    const cancelled = new AbortController();
    cancelled.abort();
    expect(() => feature.loadRequest.execute(access, leaveId(), null, cancelled.signal)).toThrow();
    const late = new AbortController();
    request.mockImplementation(async () => {
      late.abort();
      return leaveRecord();
    });
    await expect(
      feature.loadRequest.execute(access, leaveId(), null, late.signal),
    ).rejects.toThrow();
  });
});
