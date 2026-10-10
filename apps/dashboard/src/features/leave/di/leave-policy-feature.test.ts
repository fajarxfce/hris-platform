import { describe, expect, it, vi } from "vitest";
import {
  leavePolicyId,
  leavePolicyRecord,
  leavePolicyReviewPage,
} from "../../../../tests/fixtures/leave-policies";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import type { LeavePolicyReviewDto } from "../data/models/leave-type-dto";
import { createLeaveFeature } from "./leave-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["leave.manage"] };
const query = { active: null, after: null };
const signal = () => new AbortController().signal;
function newest(record: LeavePolicyReviewDto) {
  const row = record.history.items[0];
  if (!row) throw new Error("Missing fixture history");
  return row;
}

describe("administrative leave policy boundary", () => {
  it("requires administrative permission and valid identities/cursors before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
    for (const permissions of [[], ["leave.read"], ["leave.self.manage"], ["leave.approve"]]) {
      expect(
        await feature.loadPolicies.execute({ ...access, permissions }, query, signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(
        await feature.loadPolicy.execute(
          { ...access, permissions },
          leavePolicyId(),
          null,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    }
    for (const invalid of [
      { active: "TRUE", after: null },
      { active: "", after: null },
      { active: null, after: "../foreign" },
      { active: null, after: "a" },
      { active: null, after: "A".repeat(33) },
    ])
      expect(await feature.loadPolicies.execute(access, invalid, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_page" },
      });
    for (const cursor of ["-1", "01", "1.5", "Infinity", "9007199254740992", "", "../other"])
      expect(
        await feature.loadPolicy.execute(access, leavePolicyId(), cursor, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
    expect(await feature.loadPolicy.execute(access, "../other", null, signal())).toMatchObject({
      ok: false,
      failure: { code: "leave_type_not_found" },
    });
    expect(request).not.toHaveBeenCalled();
  });

  it("uses a bounded administrative catalog including future and disabled heads", async () => {
    const raw = leavePolicyRecord(0, 1, 23).current;
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ items: [raw], nextCursor: null });
    const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
    expect(
      await feature.loadPolicies.execute(access, { active: "false", after: "EARLIER" }, signal()),
    ).toMatchObject({
      ok: true,
      value: {
        items: [{ companyId, id: raw.id, version: 23, active: false, effectiveFrom: "2027-01-01" }],
      },
    });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/leave/policies?limit=20&active=false&after=EARLIER`,
    );
  });

  it("keeps current terms distinct from older history and freezes detached evidence", async () => {
    const raw = leavePolicyRecord(0, 1, 23);
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(leavePolicyReviewPage(raw));
    const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
    const first = await feature.loadPolicy.execute(
      access,
      raw.current.id.toUpperCase(),
      null,
      signal(),
    );
    expect(first).toMatchObject({
      ok: true,
      value: { current: { version: 23 }, history: { nextCursor: "4" } },
    });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/leave/policies/${raw.current.id}?historyLimit=20`,
    );
    request.mockResolvedValue(leavePolicyReviewPage(raw, 4));
    const result = await feature.loadPolicy.execute(access, raw.current.id, "4", signal());
    expect(result).toMatchObject({
      ok: true,
      value: {
        current: { version: 23, name: "North leave 01" },
        history: {
          items: [{ revision: 3 }, { revision: 2 }, { revision: 1 }, { revision: 0 }],
          nextCursor: null,
        },
      },
    });
    expect(request.mock.lastCall?.[0].path).toContain("historyLimit=20&historyAfter=4");
    if (!first.ok || !result.ok) return;
    raw.current.allowedContracts.splice(0);
    newest(raw).allowedContracts.splice(0);
    if (raw.current.accrual) raw.current.accrual.daysPerPeriod = "300";
    expect(first.value.current.allowedContracts).toEqual(["PERMANENT", "FIXED_TERM"]);
    expect(first.value.history.items[0]?.allowedContracts).toHaveLength(2);
    expect(result.value.current.accrual?.daysPerPeriod).toBe("1.5");
    expect(Object.isFrozen(result.value.current.accrual)).toBe(true);
    expect(Object.isFrozen(result.value.history.items[0])).toBe(true);
  });

  it.each<[string, (record: LeavePolicyReviewDto) => void]>([
    [
      "foreign identity",
      (raw) => {
        raw.current.id = leavePolicyId(1);
      },
    ],
    [
      "effective definition instead of latest head",
      (raw) => {
        raw.current.appliedRevision = 0;
      },
    ],
    [
      "invalid calendar date",
      (raw) => {
        raw.current.effectiveFrom = "2026-02-30";
      },
    ],
    [
      "invalid revision calendar",
      (raw) => {
        newest(raw).effectiveFrom = "2026-02-30";
      },
    ],
    [
      "invalid recorded instant",
      (raw) => {
        newest(raw).recordedAt = "not-an-instant-value-xxx";
      },
    ],
    [
      "duplicate contracts",
      (raw) => {
        raw.current.allowedContracts = ["PERMANENT", "PERMANENT"];
      },
    ],
    [
      "missing history revision",
      (raw) => {
        raw.history.items.splice(1, 1);
      },
    ],
    [
      "repeated history revision",
      (raw) => {
        raw.history.items.push(newest(raw));
      },
    ],
    [
      "truncated terminal history",
      (raw) => {
        raw.history.items.pop();
      },
    ],
    [
      "noncanonical continuation",
      (raw) => {
        raw.history.nextCursor = "01";
      },
    ],
    [
      "invalid entitlement precision",
      (raw) => {
        if (raw.current.accrual) raw.current.accrual.daysPerPeriod = "1.25";
      },
    ],
    [
      "excess entitlement",
      (raw) => {
        if (raw.current.accrual) raw.current.accrual.carryLimitDays = "367";
      },
    ],
  ])("does not render a malformed %s", async (_, mutate) => {
    const raw = leavePolicyRecord(0, 1, 2);
    mutate(raw);
    const feature = createLeaveFeature(
      { request: vi.fn().mockResolvedValue(raw) },
      { downloadBinary: vi.fn() },
    );
    expect(await feature.loadPolicy.execute(access, leavePolicyId(), null, signal())).toMatchObject(
      { ok: false, failure: { code: "invalid_response" } },
    );
  });

  it("rejects duplicate catalog rows, filter mismatches and nonprogressing page cursors", async () => {
    const raw = leavePolicyRecord().current;
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
    for (const page of [
      { items: [raw, raw], nextCursor: null },
      { items: [raw], nextCursor: raw.code },
      { items: [{ ...raw, active: true }], nextCursor: null },
    ]) {
      request.mockResolvedValue(page);
      expect(
        await feature.loadPolicies.execute(access, { active: "false", after: null }, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    request.mockResolvedValue({ items: [raw], nextCursor: null });
    expect(
      await feature.loadPolicies.execute(access, { active: null, after: raw.code }, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it("preserves safe server failures and propagates cancellation before and after acquisition", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
      new HttpResponseError(403, {
        code: "mfa_required",
        detail: "PRIVATE",
        fields: {},
        parameters: {},
      }),
    );
    const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
    expect(await feature.loadPolicy.execute(access, leavePolicyId(), null, signal())).toEqual({
      ok: false,
      failure: { code: "mfa_required", fields: {}, parameters: {} },
    });
    const cancelled = new AbortController();
    cancelled.abort();
    expect(() => feature.loadPolicies.execute(access, query, cancelled.signal)).toThrow();
    expect(request).toHaveBeenCalledTimes(1);
    let resolve!: (value: unknown) => void;
    request.mockReturnValue(
      new Promise((done) => {
        resolve = done;
      }),
    );
    const pending = new AbortController();
    const result = feature.loadPolicy.execute(access, leavePolicyId(), null, pending.signal);
    pending.abort();
    resolve(leavePolicyRecord());
    await expect(result).rejects.toMatchObject({ name: "AbortError" });
  });
});
