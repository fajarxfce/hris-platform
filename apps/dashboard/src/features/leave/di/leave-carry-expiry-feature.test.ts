import { describe, expect, it, vi } from "vitest";
import type { HttpRequest } from "../../../core/data/http/http-client";
import type { CompanyId } from "../../../core/domain/identifiers";
import {
  batchAccess,
  batchCompany,
  batchEmployee,
  batchOperation,
  deferred,
} from "./leave-batch-fixture";
import { carryExpiryId, carryWire, resumedCarryWire } from "./leave-carry-expiry-fixture";
import { createLeaveFeature } from "./leave-feature";

const signal = () => new AbortController().signal;
const setup = (response: unknown = carryWire) => {
  const request = vi
    .fn<(input: HttpRequest, signal: AbortSignal) => Promise<unknown>>()
    .mockResolvedValue(response);
  return { request, feature: createLeaveFeature({ request }, { downloadBinary: vi.fn() }) };
};
describe("carry expiry HTTP and domain boundaries", () => {
  it("acquires one bounded page and owns its employee and attempt snapshots", async () => {
    const wire = structuredClone(carryWire),
      f = setup(wire);
    const result = await f.feature.loadCarryExpiry.execute(
      batchAccess,
      carryExpiryId,
      null,
      signal(),
    );
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${batchCompany}/leave/carry-expiries/${carryExpiryId}?limit=20`,
    });
    const employee = wire.employees[0],
      attempt = wire.attempts[0];
    if (!employee || !attempt) throw Error("Missing fixture records");
    employee.name = "Changed later";
    attempt.reason = "Changed later";
    expect(result.value.results.items[0]?.employee.name).toBe("Example Employee");
    expect(result.value.attempts[0]?.reason).toBe("Reviewed year closing");
    expect(Object.isFrozen(result.value.results.items)).toBe(true);
    expect(Object.isFrozen(result.value.attempts)).toBe(true);
    expect(result.value.job).not.toHaveProperty("credentialVersion");
  });
  it("retains completed outcomes from an earlier attempt after recovery", async () => {
    const result = await setup(resumedCarryWire()).feature.loadCarryExpiry.execute(
      batchAccess,
      carryExpiryId,
      null,
      signal(),
    );
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.value.expiry.version).toBe(1);
      expect(result.value.results.items[0]?.jobId).toBe(carryWire.job.id);
      expect(result.value.job.totalItems).toBe(2);
      expect(result.value.actions).toEqual([]);
    }
  });
  it("rejects missing read scope and foreign recovery scope before I/O", async () => {
    const f = setup(),
      reader = { ...batchAccess, permissions: ["leave.read"] };
    expect(
      await f.feature.loadCarryExpiry.execute(
        { ...reader, permissions: [] },
        carryExpiryId,
        null,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await f.feature.loadCarryExpiries.execute({ ...reader, permissions: [] }, null, signal()),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    const input = {
      companyId: batchCompany,
      id: carryExpiryId,
      expectedVersion: 0,
      reason: "Resume work",
    };
    expect(
      await f.feature.resumeCarryExpiry.execute(reader, batchOperation, input, signal()),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(
      await f.feature.resumeCarryExpiry.execute(
        batchAccess,
        batchOperation,
        { ...input, companyId: batchEmployee(99) as CompanyId },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
    expect(f.request).not.toHaveBeenCalled();
  });
  it.each(["0", "-1", "5001", "01", "x"])(
    "rejects nonprogressing or out-of-range outcome cursor %s",
    async (after) => {
      const f = setup();
      expect(
        await f.feature.loadCarryExpiry.execute(batchAccess, carryExpiryId, after, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_page" } });
      expect(f.request).not.toHaveBeenCalled();
    },
  );
  it.each(["origin", "job", "employee", "prefix", "ledger", "extra-employee", "cursor"])(
    "rejects an inconsistent or foreign %s response",
    async (problem) => {
      const wire = structuredClone(carryWire),
        outcome = wire.results.items[0];
      if (!outcome) throw Error("Missing fixture outcome");
      if (problem === "origin") wire.expiry.id = batchEmployee(91);
      if (problem === "job") wire.job.id = batchEmployee(92);
      if (problem === "employee") outcome.employeeId = batchEmployee(93);
      if (problem === "prefix") outcome.ordinal = 2;
      if (problem === "ledger") outcome.ledgerId = null;
      if (problem === "extra-employee")
        wire.employees.push({ id: batchEmployee(94), name: "Private", number: null });
      if (problem === "cursor") wire.results.nextCursor = "2";
      expect(
        await setup(wire).feature.loadCarryExpiry.execute(
          batchAccess,
          carryExpiryId,
          null,
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    },
  );
  it("rejects repeated directory IDs and a cursor that would replay the same page", async () => {
    const wire = { items: [carryWire.expiry, carryWire.expiry], nextCursor: null };
    expect(
      await setup(wire).feature.loadCarryExpiries.execute(batchAccess, null, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    expect(
      await setup({
        items: [carryWire.expiry],
        nextCursor: carryExpiryId,
      }).feature.loadCarryExpiries.execute(batchAccess, null, signal()),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("captures one immutable recovery command and validates its returned version", async () => {
    const held = deferred<unknown>(),
      f = setup();
    f.request.mockReturnValue(held.promise);
    const input = {
      companyId: batchCompany,
      id: carryExpiryId,
      expectedVersion: 0,
      reason: "  Continue remaining work  ",
    };
    const pending = f.feature.resumeCarryExpiry.execute(
      batchAccess,
      batchOperation,
      input,
      signal(),
    );
    input.reason = "Changed after submission";
    input.expectedVersion = 7;
    held.resolve({ id: carryExpiryId, version: 1 });
    expect(await pending).toEqual({ ok: true, value: { id: carryExpiryId, version: 1 } });
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${batchCompany}/leave/carry-expiries/${carryExpiryId}/resume`,
      method: "POST",
      operationId: batchOperation,
      body: { expectedVersion: 0, reason: "Continue remaining work" },
    });
    expect(
      await setup({ id: carryExpiryId, version: 2 }).feature.resumeCarryExpiry.execute(
        batchAccess,
        batchOperation,
        { ...input, expectedVersion: 0 },
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("does not publish a response that arrives after cancellation", async () => {
    const held = deferred<unknown>(),
      f = setup(),
      controller = new AbortController();
    f.request.mockReturnValue(held.promise);
    const pending = f.feature.loadCarryExpiry.execute(
      batchAccess,
      carryExpiryId,
      null,
      controller.signal,
    );
    controller.abort();
    held.resolve(carryWire);
    await expect(pending).rejects.toMatchObject({ name: "AbortError" });
  });
});
