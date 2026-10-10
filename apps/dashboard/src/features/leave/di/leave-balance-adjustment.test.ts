import { expect, it, vi } from "vitest";
import { leaveEmployeeId } from "../../../../tests/fixtures/leave";
import {
  balanceDirectoryPage,
  balanceEntryId,
  balanceLedgerPage,
  balanceTypeId,
} from "../../../../tests/fixtures/leave-balances";
import { leavePolicyRecord } from "../../../../tests/fixtures/leave-policies";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { LeaveBalanceAdjustment } from "../domain/entities/leave-balance-adjustment";
import { createLeaveFeature } from "./leave-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["leave.read", "leave.manage"] };
const operation = "db000000-0000-4000-8000-000000000001" as OperationId;
const signal = () => new AbortController().signal;
const input: LeaveBalanceAdjustment = {
  employeeId: leaveEmployeeId(),
  typeId: balanceTypeId(),
  year: 2026,
  days: "+1.50",
  reason: " Reviewed opening balance ",
  expectedVersion: 14,
};
function fixture(request = vi.fn<HttpClient["request"]>()) {
  return { request, feature: createLeaveFeature({ request }, { downloadBinary: vi.fn() }) };
}

it("requires management and balance-read scope for a review/catalog before any acquisition", async () => {
  const { feature, request } = fixture();
  for (const permissions of [
    [],
    ["leave.manage"],
    ["leave.read"],
    ["leave.approve"],
    ["people.read"],
  ]) {
    const denied = { ...access, permissions };
    expect(
      await feature.reviewBalanceAdjustment.execute(
        denied,
        input.employeeId,
        input.typeId,
        "2026",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await feature.loadAdjustmentCatalog.execute(
        denied,
        input.employeeId,
        { year: "2026", after: null },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
  }
  expect(
    await feature.adjustBalance.execute(
      { ...access, permissions: ["leave.read"] },
      operation,
      input,
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
  expect(request).not.toHaveBeenCalled();
});

it("normalizes signed half-days and sends an observed version with an immutable movement receipt", async () => {
  const { request, feature } = fixture();
  request.mockResolvedValue({ id: balanceEntryId(100).toUpperCase(), version: 0 });
  const result = await feature.adjustBalance.execute(access, operation, input, signal());
  expect(result).toEqual({ ok: true, value: { id: balanceEntryId(100), version: 0 } });
  expect(request.mock.lastCall?.[0]).toEqual({
    path: `/api/v1/companies/${companyId}/leave/employees/${input.employeeId}/balances/${input.typeId}/2026/adjustments`,
    method: "POST",
    operationId: operation,
    body: { days: "1.5", reason: "Reviewed opening balance", expectedVersion: 14 },
  });
  expect(input.days).toBe("+1.50");
  expect(result.ok && Object.isFrozen(result.value)).toBe(true);
  for (const days of ["-0.5", "366", "-366"])
    expect(
      (await feature.adjustBalance.execute(access, operation, { ...input, days }, signal())).ok,
    ).toBe(true);
});

it.each([
  "",
  "0",
  "-0.00",
  "1.25",
  "1e2",
  "Infinity",
  "1,5",
  "367",
  "-366.5",
  "01",
  "0.500",
  "--1",
  "1.5000000001",
])("rejects invalid adjustment amount %s before I/O", async (days) => {
  const { request, feature } = fixture();
  expect(
    await feature.adjustBalance.execute(access, operation, { ...input, days }, signal()),
  ).toMatchObject({
    ok: false,
    failure: { code: "invalid_leave_adjustment", fields: { days: "invalid_leave_days" } },
  });
  expect(request).not.toHaveBeenCalled();
});

it.each<Partial<LeaveBalanceAdjustment>>([
  { employeeId: "../another" },
  { typeId: "../another" },
  { year: 1899 },
  { year: 2201 },
  { year: 2026.5 },
  { expectedVersion: -1 },
  { expectedVersion: 1.5 },
  { expectedVersion: Number.MAX_SAFE_INTEGER },
  { reason: " " },
  { reason: "a".repeat(1001) },
])("rejects an invalid target, version, or reason", async (change) => {
  const { request, feature } = fixture();
  expect(
    await feature.adjustBalance.execute(access, operation, { ...input, ...change }, signal()),
  ).toMatchObject({ ok: false, failure: { code: "invalid_leave_adjustment" } });
  expect(request).not.toHaveBeenCalled();
});

it("does not mistake an account version, malformed ID, or missing receipt for acknowledgement", async () => {
  const { request, feature } = fixture();
  for (const response of [
    { id: balanceEntryId(), version: 15 },
    { id: "invalid", version: 0 },
    null,
  ]) {
    request.mockResolvedValueOnce(response);
    expect(await feature.adjustBalance.execute(access, operation, input, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  }
  expect(request).toHaveBeenCalledTimes(3);
});

it("accepts only known current actions, fails closed for older servers and denies a closed account", async () => {
  const { request, feature } = fixture();
  const raw = balanceLedgerPage();
  raw.availableActions = ["ADJUST", "FUTURE_ACTION"];
  request.mockResolvedValue(raw);
  const result = await feature.reviewBalanceAdjustment.execute(
    access,
    input.employeeId,
    input.typeId,
    "2026",
    signal(),
  );
  expect(result).toMatchObject({ ok: true, value: { availableActions: ["ADJUST"] } });
  if (result.ok) expect(Object.isFrozen(result.value.availableActions)).toBe(true);
  for (const response of [
    { ...raw, availableActions: ["FUTURE_ACTION"] },
    { ...raw, availableActions: undefined },
    { ...raw, balance: { ...raw.balance, closed: true } },
  ]) {
    request.mockResolvedValue(response);
    expect(
      await feature.reviewBalanceAdjustment.execute(
        access,
        input.employeeId,
        input.typeId,
        "2026",
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "leave_adjustment_unavailable" } });
  }
  request.mockResolvedValue({ ...raw, availableActions: ["ADJUST", "ADJUST"] });
  expect(
    await feature.reviewBalanceAdjustment.execute(
      access,
      input.employeeId,
      input.typeId,
      "2026",
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
});

it("bounds review/catalog identities, year, and cursor before acquiring data", async () => {
  const { request, feature } = fixture();
  expect(
    await feature.reviewBalanceAdjustment.execute(access, "bad", input.typeId, "2026", signal()),
  ).toMatchObject({ ok: false, failure: { code: "employee_not_found" } });
  expect(
    await feature.reviewBalanceAdjustment.execute(
      access,
      input.employeeId,
      "bad",
      "2026",
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "leave_type_not_found" } });
  for (const year of ["2026.0", "1899", "2201", "2e3"])
    expect(
      await feature.reviewBalanceAdjustment.execute(
        access,
        input.employeeId,
        input.typeId,
        year,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
  for (const query of [
    { year: "bad", after: null },
    { year: "2026", after: "bad" },
  ])
    expect(
      await feature.loadAdjustmentCatalog.execute(access, input.employeeId, query, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
  expect(
    await feature.loadAdjustmentCatalog.execute(
      access,
      "bad",
      { year: "2026", after: null },
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "employee_not_found" } });
  expect(request).not.toHaveBeenCalled();
});

it("loads minimal employee context before a bounded catalog, including types with no balance yet", async () => {
  const { request, feature } = fixture();
  request
    .mockResolvedValueOnce(balanceDirectoryPage(2027))
    .mockResolvedValueOnce({ items: [leavePolicyRecord().current], nextCursor: null });
  const result = await feature.loadAdjustmentCatalog.execute(
    access,
    input.employeeId.toUpperCase(),
    { year: "2027", after: "LEAVE000" },
    signal(),
  );
  expect(result).toMatchObject({
    ok: true,
    value: {
      companyId,
      employee: { id: input.employeeId },
      year: 2027,
      policies: { items: [{ active: false }] },
    },
  });
  expect(request.mock.calls.map((call) => call[0].path)).toEqual([
    `/api/v1/companies/${companyId}/leave/employees/${input.employeeId}/balances?year=2027&limit=20`,
    `/api/v1/companies/${companyId}/leave/policies?limit=20&after=LEAVE000`,
  ]);
});

it("preserves failure/cancellation and never starts a second catalog acquisition after cancellation", async () => {
  const { request, feature } = fixture();
  request.mockRejectedValueOnce(
    new HttpResponseError(403, { code: "mfa_required", detail: "PRIVATE" }),
  );
  expect(await feature.adjustBalance.execute(access, operation, input, signal())).toEqual({
    ok: false,
    failure: { code: "mfa_required", fields: {}, parameters: {} },
  });
  request.mockRejectedValueOnce(new HttpResponseError(404, { code: "employee_not_found" }));
  expect(
    await feature.loadAdjustmentCatalog.execute(
      access,
      input.employeeId,
      { year: "2026", after: null },
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "employee_not_found" } });
  const abort = new AbortController();
  request.mockImplementationOnce(async () => {
    abort.abort();
    return balanceDirectoryPage();
  });
  await expect(
    feature.loadAdjustmentCatalog.execute(
      access,
      input.employeeId,
      { year: "2026", after: null },
      abort.signal,
    ),
  ).rejects.toThrow();
  expect(request).toHaveBeenCalledTimes(3);
  expect(() => feature.adjustBalance.execute(access, operation, input, abort.signal)).toThrow();
  expect(request).toHaveBeenCalledTimes(3);
});
