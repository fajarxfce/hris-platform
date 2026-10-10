import { expect, it, vi } from "vitest";
import { leavePolicyId } from "../../../../tests/fixtures/leave-policies";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { LeavePolicyChange } from "../domain/entities/leave-policy-change";
import { createLeaveFeature } from "./leave-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: ["leave.manage"] };
const operation = "b7000000-0000-4000-8000-000000000001" as OperationId;
const signal = () => new AbortController().signal;
const change: LeavePolicyChange = {
  id: leavePolicyId(),
  code: "ANNUAL",
  name: "Annual leave",
  effectiveFrom: "2027-01-01",
  paid: true,
  allowPartialDays: true,
  minServiceMonths: 12,
  maxRequestDays: 30,
  allowedContracts: ["PERMANENT", "FIXED_TERM"],
  active: true,
  attachmentRequired: true,
  expectedVersion: null,
  reason: "Annual policy review",
  accrual: { frequency: "MONTHLY", daysPerPeriod: "1.5", carryLimitDays: "3.5" },
};

it("normalizes one scoped policy command with exact observed version and decimal strings", async () => {
  const request = vi
    .fn<HttpClient["request"]>()
    .mockResolvedValue({ id: change.id.toUpperCase(), version: 0 });
  const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
  const result = await feature.savePolicy.execute(
    access,
    operation,
    {
      ...change,
      id: change.id.toUpperCase(),
      code: " annual ",
      name: " Annual leave ",
      reason: " Annual policy review ",
      accrual: { frequency: "ANNUAL", daysPerPeriod: "12.0", carryLimitDays: "3.5" },
    },
    signal(),
  );
  expect(result).toEqual({ ok: true, value: { id: change.id, version: 0 } });
  expect(request.mock.lastCall?.[0]).toEqual({
    path: `/api/v1/companies/${companyId}/leave/types/${change.id}`,
    method: "PUT",
    operationId: operation,
    body: {
      code: "ANNUAL",
      name: "Annual leave",
      effectiveFrom: "2027-01-01",
      paid: true,
      allowPartialDays: true,
      minServiceMonths: 12,
      maxRequestDays: 30,
      allowedContracts: ["FIXED_TERM", "PERMANENT"],
      active: true,
      attachmentRequired: true,
      expectedVersion: null,
      reason: "Annual policy review",
      accrual: { frequency: "ANNUAL", daysPerPeriod: "12", carryLimitDays: "3.5" },
    },
  });
  request.mockResolvedValue({ id: change.id, version: 24 });
  expect(
    await feature.savePolicy.execute(
      access,
      operation,
      { ...change, expectedVersion: 23, active: false, accrual: null },
      signal(),
    ),
  ).toMatchObject({ ok: true, value: { version: 24 } });
  expect(request.mock.lastCall?.[0].body).toMatchObject({
    expectedVersion: 23,
    active: false,
    accrual: null,
  });
});

it("requires current administrative scope and an operation identity before delivering a policy", async () => {
  const request = vi.fn<HttpClient["request"]>();
  const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
  expect(
    await feature.savePolicy.execute(
      { ...access, permissions: ["leave.read"] },
      operation,
      change,
      signal(),
    ),
  ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
  expect(
    await feature.savePolicy.execute(access, "invalid" as OperationId, change, signal()),
  ).toMatchObject({ ok: false, failure: { code: "invalid_leave_policy" } });
  expect(request).not.toHaveBeenCalled();
});

it.each<[string, Partial<LeavePolicyChange>, string]>([
  ["blank name", { name: "   " }, "name"],
  ["blank reason", { reason: "  " }, "reason"],
  ["invalid code", { code: "1 invalid" }, "code"],
  ["invalid date", { effectiveFrom: "2027-02-29" }, "effectiveFrom"],
  ["fractional service", { minServiceMonths: 1.5 }, "minServiceMonths"],
  ["empty numeric input", { minServiceMonths: Number.NaN }, "minServiceMonths"],
  ["unbounded request", { maxRequestDays: 367 }, "maxRequestDays"],
  ["no contract", { allowedContracts: [] }, "allowedContracts"],
  ["duplicate contracts", { allowedContracts: ["PERMANENT", "PERMANENT"] }, "allowedContracts"],
  ["version overflow", { expectedVersion: Number.MAX_SAFE_INTEGER }, "expectedVersion"],
  [
    "monthly excess",
    { accrual: { frequency: "MONTHLY", daysPerPeriod: "31.5", carryLimitDays: "0" } },
    "accrual.daysPerPeriod",
  ],
  [
    "quarter day",
    { accrual: { frequency: "MONTHLY", daysPerPeriod: "1.25", carryLimitDays: "0" } },
    "accrual.daysPerPeriod",
  ],
  [
    "manual allowance",
    { accrual: { frequency: "MANUAL", daysPerPeriod: "1", carryLimitDays: "0" } },
    "accrual.daysPerPeriod",
  ],
  [
    "zero annual allowance",
    { accrual: { frequency: "ANNUAL", daysPerPeriod: "0", carryLimitDays: "0" } },
    "accrual.daysPerPeriod",
  ],
  ["whole-day entitlement", { allowPartialDays: false }, "accrual.daysPerPeriod"],
  [
    "whole-day carry",
    {
      allowPartialDays: false,
      accrual: { frequency: "ANNUAL", daysPerPeriod: "12", carryLimitDays: "0.5" },
    },
    "accrual.carryLimitDays",
  ],
  [
    "carry excess",
    { accrual: { frequency: "ANNUAL", daysPerPeriod: "12", carryLimitDays: "367" } },
    "accrual.carryLimitDays",
  ],
])("rejects %s before I/O with a field failure", async (_, fields, field) => {
  const request = vi.fn<HttpClient["request"]>();
  const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
  const result = await feature.savePolicy.execute(
    access,
    operation,
    { ...change, ...fields },
    signal(),
  );
  expect(result.ok).toBe(false);
  if (!result.ok) expect(result.failure.fields[field]).toBeDefined();
  expect(request).not.toHaveBeenCalled();
});

it.each([
  { id: leavePolicyId(1), version: 0 },
  { id: leavePolicyId(), version: 1 },
  { id: leavePolicyId(), version: 0.5 },
])("rejects mismatched or malformed receipts without claiming success: %j", async (receipt) => {
  const feature = createLeaveFeature(
    { request: vi.fn().mockResolvedValue(receipt) },
    { downloadBinary: vi.fn() },
  );
  expect(await feature.savePolicy.execute(access, operation, change, signal())).toMatchObject({
    ok: false,
    failure: { code: "invalid_response" },
  });
});

it("preserves server validation and propagates cancellation after a pending save", async () => {
  const request = vi.fn<HttpClient["request"]>().mockRejectedValue(
    new HttpResponseError(422, {
      code: "invalid_leave_accrual_policy",
      fields: { "accrual.daysPerPeriod": "out_of_range" },
      parameters: {},
      detail: "PRIVATE",
    }),
  );
  const feature = createLeaveFeature({ request }, { downloadBinary: vi.fn() });
  expect(await feature.savePolicy.execute(access, operation, change, signal())).toEqual({
    ok: false,
    failure: {
      code: "invalid_leave_accrual_policy",
      fields: { "accrual.daysPerPeriod": "out_of_range" },
      parameters: {},
    },
  });
  let resolve!: (value: unknown) => void;
  request.mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  const pending = new AbortController();
  const saved = feature.savePolicy.execute(access, operation, change, pending.signal);
  pending.abort();
  resolve({ id: change.id, version: 0 });
  await expect(saved).rejects.toMatchObject({ name: "AbortError" });
});
