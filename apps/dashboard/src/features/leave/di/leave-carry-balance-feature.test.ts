import { expect, it, vi } from "vitest";
import { leaveEmployeeId } from "../../../../tests/fixtures/leave";
import {
  balanceDirectoryPage,
  balanceLedgerPage,
  balanceTypeId,
  carriedBalance,
} from "../../../../tests/fixtures/leave-balances";
import type { HttpClient } from "../../../core/data/http/http-client";
import type { CompanyId } from "../../../core/domain/identifiers";
import type { LeaveLedgerDto } from "../data/models/leave-ledger-dto";
import { leaveLedgerView } from "../presentation/models/leave-balance-view";
import { createLeaveFeature } from "./leave-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["leave.read"] };
const query = { year: "2026", after: null };
const signal = () => new AbortController().signal;

it("retains a detached carried breakdown in directory and ledger reads, including the original UTC deadline", async () => {
  const directory = balanceDirectoryPage();
  const raw = balanceLedgerPage();
  const carry = carriedBalance();
  carry.expiresAt = "2026-03-31T18:00:00Z";
  raw.balance.carry = carry;
  const first = directory.balances.items[0];
  if (!first) throw new Error("Missing balance fixture");
  first.balance.carry = carry;
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValueOnce(directory)
    .mockResolvedValueOnce(raw);
  const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
  const page = await feature.loadBalances.execute(access, leaveEmployeeId(), query, signal());
  const result = await feature.loadLedger.execute(
    access,
    leaveEmployeeId(),
    balanceTypeId(),
    query,
    signal(),
  );
  expect(page.ok).toBe(true);
  expect(result.ok).toBe(true);
  if (!result.ok || !page.ok) return;
  expect(result.value.balance.carry).toEqual(carry);
  expect(page.value.items[0]?.balance.carry).toEqual(carry);
  expect(Object.isFrozen(result.value.balance.carry)).toBe(true);
  carry.availableDays = "90";
  carry.timezone = "Invalid/Changed";
  expect(result.value.balance.carry?.availableDays).toBe("2.5");
  expect(page.value.items[0]?.balance.carry?.availableDays).toBe("2.5");
  const view = leaveLedgerView(result.value, "id", "America/Los_Angeles");
  expect(view.carry).toContainEqual({ label: "Berlaku hingga", value: "31 Mar 2026" });
  expect(view.carry).toContainEqual({ label: "Hari tersedia", value: "2,5" });
  expect(view.carry).toContainEqual({ label: "Zona waktu pemberian", value: "Asia/Jakarta" });
  expect(view.properties).toContainEqual({ label: "Hari tersedia", value: "8,5" });
});

it.each([undefined, null, "unlimited"] as const)(
  "accepts %s carry without inventing a deadline or losing an ordinary balance",
  async (kind) => {
    const raw = balanceLedgerPage();
    raw.balance.carry =
      kind === "unlimited" ? { ...carriedBalance(), validThrough: null, expiresAt: null } : kind;
    const feature = createLeaveFeature(
      { request: vi.fn().mockResolvedValue(raw) },
      { downloadBinary: vi.fn() },
    );
    const result = await feature.loadLedger.execute(
      access,
      leaveEmployeeId(),
      balanceTypeId(),
      query,
      signal(),
    );
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value.balance.availableDays).toBe("8.5");
    const view = leaveLedgerView(result.value, "en", "Asia/Jakarta");
    if (kind === "unlimited")
      expect(view.carry).toContainEqual({
        label: "Valid through",
        value: "No additional deadline",
      });
    else {
      expect(result.value.balance.carry).toBeNull();
      expect(view.carry).toBeNull();
    }
  },
);

it.each<[string, (raw: LeaveLedgerDto) => void]>([
  [
    "non-conservation",
    (raw) => {
      raw.balance.carry = { ...carriedBalance(), awardedDays: "5.5" };
    },
  ],
  [
    "carried availability exceeds the main account",
    (raw) => {
      raw.balance.availableDays = "2";
    },
  ],
  [
    "carried reservation exceeds the main account",
    (raw) => {
      raw.balance.reservedDays = "0";
    },
  ],
  [
    "carried consumption exceeds the main account",
    (raw) => {
      raw.balance.consumedDays = "1";
    },
  ],
  [
    "closed year retains free carry",
    (raw) => {
      raw.balance.closed = true;
    },
  ],
  [
    "wrong year",
    (raw) => {
      raw.balance.carry = { ...carriedBalance(), validThrough: "2025-03-31" };
    },
  ],
  [
    "incomplete month",
    (raw) => {
      raw.balance.carry = { ...carriedBalance(), validThrough: "2026-03-30" };
    },
  ],
  [
    "invalid calendar date",
    (raw) => {
      raw.balance.carry = { ...carriedBalance(), validThrough: "2026-02-30" };
    },
  ],
  [
    "missing UTC boundary",
    (raw) => {
      raw.balance.carry = { ...carriedBalance(), expiresAt: null };
    },
  ],
  [
    "missing calendar boundary",
    (raw) => {
      raw.balance.carry = { ...carriedBalance(), validThrough: null };
    },
  ],
  [
    "invalid UTC boundary",
    (raw) => {
      raw.balance.carry = { ...carriedBalance(), expiresAt: "PRIVATE RAW DETAIL" };
    },
  ],
  [
    "invalid timezone",
    (raw) => {
      raw.balance.carry = { ...carriedBalance(), timezone: "Invalid/Private" };
    },
  ],
  [
    "fractional precision",
    (raw) => {
      raw.balance.carry = { ...carriedBalance(), consumedDays: "1.25" };
    },
  ],
  [
    "oversized award",
    (raw) => {
      raw.balance.carry = { ...carriedBalance(), awardedDays: "367" };
    },
  ],
  [
    "unsafe version",
    (raw) => {
      raw.balance.carry = { ...carriedBalance(), version: Number.MAX_SAFE_INTEGER + 1 };
    },
  ],
  [
    "uncreated balance",
    (raw) => {
      raw.balance = {
        ...raw.balance,
        accountId: null,
        version: 0,
        availableDays: "0",
        reservedDays: "0",
        consumedDays: "0",
      };
    },
  ],
])("rejects %s within the HTTP failure boundary", async (_name, mutate) => {
  const raw = balanceLedgerPage();
  raw.balance.carry = carriedBalance();
  mutate(raw);
  const feature = createLeaveFeature(
    { request: vi.fn().mockResolvedValue(raw) },
    { downloadBinary: vi.fn() },
  );
  const result = await feature.loadLedger.execute(
    access,
    leaveEmployeeId(),
    balanceTypeId(),
    query,
    signal(),
  );
  expect(result).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  expect(JSON.stringify(result)).not.toContain("PRIVATE");
});
