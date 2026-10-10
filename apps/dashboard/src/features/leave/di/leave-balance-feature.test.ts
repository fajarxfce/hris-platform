import { describe, expect, it, vi } from "vitest";
import { leaveEmployeeId } from "../../../../tests/fixtures/leave";
import {
  balanceDirectoryPage,
  balanceEntryId,
  balanceLedgerPage,
  balanceTypeId,
} from "../../../../tests/fixtures/leave-balances";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import type { EmployeeLeaveBalancesDto } from "../data/models/leave-balance-dto";
import type { LeaveLedgerDto } from "../data/models/leave-ledger-dto";
import { createLeaveFeature } from "./leave-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["leave.read"] };
const query = { year: "2026", after: null };
const signal = () => new AbortController().signal;

function balanceAt(raw: EmployeeLeaveBalancesDto, index = 0) {
  const row = raw.balances.items[index];
  if (!row) throw new Error("Missing fixture balance");
  return row;
}
function entryAt(raw: LeaveLedgerDto, index = 0) {
  const row = raw.entries.items[index];
  if (!row) throw new Error("Missing fixture movement");
  return row;
}

describe("scoped balance acquisition", () => {
  it("requires balance scope and bounded identities/years/cursors before I/O", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
    for (const permissions of [[], ["leave.manage"], ["people.read"], ["leave.approve"]]) {
      expect(
        await feature.loadBalances.execute(
          { ...access, permissions },
          leaveEmployeeId(),
          query,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(
        await feature.loadLedger.execute(
          { ...access, permissions },
          leaveEmployeeId(),
          balanceTypeId(),
          query,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    }
    for (const year of ["", "1899", "2201", "2026.0", "2e3", " 2026", "20260"]) {
      expect(
        await feature.loadBalances.execute(access, leaveEmployeeId(), { ...query, year }, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
      expect(
        await feature.loadLedger.execute(
          access,
          leaveEmployeeId(),
          balanceTypeId(),
          { ...query, year },
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
    }
    expect(await feature.loadBalances.execute(access, "../foreign", query, signal())).toMatchObject(
      { ok: false, failure: { code: "employee_not_found" } },
    );
    expect(
      await feature.loadLedger.execute(access, leaveEmployeeId(), "../foreign", query, signal()),
    ).toMatchObject({ ok: false, failure: { code: "leave_type_not_found" } });
    expect(
      await feature.loadBalances.execute(
        access,
        leaveEmployeeId(),
        { ...query, after: "a" },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
    expect(
      await feature.loadLedger.execute(
        access,
        leaveEmployeeId(),
        balanceTypeId(),
        { ...query, after: "a" },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
    expect(request).not.toHaveBeenCalled();
  });

  it("keeps exact current balances separate from an older ledger page and freezes detached values", async () => {
    const directory = balanceDirectoryPage();
    const raw = balanceLedgerPage(2026, balanceEntryId(20));
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValueOnce(directory)
      .mockResolvedValueOnce(raw);
    const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
    const page = await feature.loadBalances.execute(
      access,
      leaveEmployeeId().toUpperCase(),
      query,
      signal(),
    );
    expect(page).toMatchObject({
      ok: true,
      value: { companyId, year: 2026, nextCursor: "TYPE020" },
    });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/leave/employees/${leaveEmployeeId()}/balances?year=2026&limit=20`,
    );
    const ledger = await feature.loadLedger.execute(
      access,
      leaveEmployeeId(),
      balanceTypeId().toUpperCase(),
      { ...query, after: balanceEntryId(20).toUpperCase() },
      signal(),
    );
    expect(ledger).toMatchObject({
      ok: true,
      value: {
        balance: { availableDays: "8.5", version: 14 },
        entries: [
          { id: balanceEntryId(21) },
          { id: balanceEntryId(22) },
          { id: balanceEntryId(23) },
        ],
        nextCursor: null,
      },
    });
    expect(request.mock.lastCall?.[0].path).toBe(
      `/api/v1/companies/${companyId}/leave/employees/${leaveEmployeeId()}/balances/${balanceTypeId()}/2026?limit=20&after=${balanceEntryId(20)}`,
    );
    if (!page.ok || !ledger.ok) return;
    raw.employee.name = "Changed";
    raw.balance.availableDays = "900";
    raw.entries.items.splice(0);
    directory.balances.items.splice(0);
    expect(ledger.value.employee.name).toBe("North Employee 01");
    expect(ledger.value.balance.availableDays).toBe("8.5");
    expect(Object.isFrozen(ledger.value.entries[0])).toBe(true);
    expect(Object.isFrozen(ledger.value.balance)).toBe(true);
    expect(page.value.items).toHaveLength(20);
  });

  it("permits scoped self/team reads without administrative grants and accepts empty years", async () => {
    const raw = balanceLedgerPage(2027);
    raw.employee.name = null;
    raw.employee.number = null;
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue(raw);
    const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
    for (const permission of ["leave.self.manage", "leave.team.read"])
      expect(
        await feature.loadLedger.execute(
          { ...access, permissions: [permission] },
          leaveEmployeeId(),
          balanceTypeId(),
          { year: "2027", after: null },
          signal(),
        ),
      ).toMatchObject({
        ok: true,
        value: { employee: { name: null }, balance: { accountId: null, version: 0 }, entries: [] },
      });
    request.mockResolvedValue(balanceDirectoryPage(2027));
    expect(
      await feature.loadBalances.execute(
        access,
        leaveEmployeeId(),
        { year: "2027", after: null },
        signal(),
      ),
    ).toMatchObject({ ok: true, value: { items: [] } });
  });

  it.each<[string, (raw: EmployeeLeaveBalancesDto) => void]>([
    [
      "foreign employee",
      (raw) => {
        raw.employee.id = leaveEmployeeId(1);
      },
    ],
    [
      "wrong year",
      (raw) => {
        raw.year = 2027;
      },
    ],
    [
      "repeated account",
      (raw) => {
        balanceAt(raw, 1).balance.accountId = balanceAt(raw).balance.accountId;
      },
    ],
    [
      "repeated type",
      (raw) => {
        balanceAt(raw, 1).typeId = balanceAt(raw).typeId;
      },
    ],
    [
      "repeated code",
      (raw) => {
        balanceAt(raw, 1).typeCode = balanceAt(raw).typeCode;
      },
    ],
    [
      "different balance year",
      (raw) => {
        balanceAt(raw).balance.year = 2025;
      },
    ],
    [
      "invented empty account",
      (raw) => {
        balanceAt(raw).balance.accountId = null;
      },
    ],
    [
      "excess precision",
      (raw) => {
        balanceAt(raw).balance.availableDays = "1.25";
      },
    ],
    [
      "negative balance",
      (raw) => {
        balanceAt(raw).balance.availableDays = "-1";
      },
    ],
    [
      "overflow",
      (raw) => {
        balanceAt(raw).balance.availableDays = "1073741824";
      },
    ],
    [
      "nonprogressing cursor",
      (raw) => {
        raw.balances.nextCursor = "TYPE001";
      },
    ],
  ])("rejects directory data with %s", async (_, mutate) => {
    const raw = balanceDirectoryPage();
    mutate(raw);
    const feature = createLeaveFeature(
      { request: vi.fn().mockResolvedValue(raw) },
      { downloadBinary: vi.fn() },
    );
    expect(
      await feature.loadBalances.execute(access, leaveEmployeeId(), query, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it.each<[string, (raw: LeaveLedgerDto) => void]>([
    [
      "foreign employee",
      (raw) => {
        raw.employee.id = leaveEmployeeId(1);
      },
    ],
    [
      "foreign type",
      (raw) => {
        raw.typeId = balanceTypeId(2);
      },
    ],
    [
      "wrong year",
      (raw) => {
        raw.balance.year = 2025;
      },
    ],
    [
      "invalid instant",
      (raw) => {
        entryAt(raw).recordedAt = "2026-02-30T12:00:00Z";
      },
    ],
    [
      "unordered movements",
      (raw) => {
        raw.entries.items.reverse();
      },
    ],
    [
      "duplicate movements",
      (raw) => {
        raw.entries.items[1] = entryAt(raw);
      },
    ],
    [
      "nonprogressing cursor",
      (raw) => {
        raw.entries.nextCursor = balanceEntryId();
      },
    ],
    [
      "empty account with movements",
      (raw) => {
        raw.balance.accountId = null;
      },
    ],
  ])("rejects ledger data with %s", async (_, mutate) => {
    const raw = balanceLedgerPage();
    mutate(raw);
    const feature = createLeaveFeature(
      { request: vi.fn().mockResolvedValue(raw) },
      { downloadBinary: vi.fn() },
    );
    expect(
      await feature.loadLedger.execute(access, leaveEmployeeId(), balanceTypeId(), query, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });

  it("preserves safe server failures and caller cancellation without retrying", async () => {
    const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
      new HttpResponseError(403, {
        code: "mfa_required",
        fields: {},
        parameters: {},
        detail: "PRIVATE",
      }),
    );
    const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
    expect(
      await feature.loadBalances.execute(access, leaveEmployeeId(), query, signal()),
    ).toMatchObject({ ok: false, failure: { code: "mfa_required" } });
    expect(request).toHaveBeenCalledTimes(1);
    const pending = new AbortController();
    pending.abort();
    expect(() =>
      feature.loadLedger.execute(access, leaveEmployeeId(), balanceTypeId(), query, pending.signal),
    ).toThrow();
    expect(request).toHaveBeenCalledTimes(1);
  });
});
