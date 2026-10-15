import { describe, expect, it, vi } from "vitest";
import type { HttpRequest } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { LeaveBatchId } from "../domain/entities/leave-batch";
import {
  batchAccess,
  batchCompany,
  batchEmployee,
  batchId,
  batchOperation,
  batchReviewWire,
  batchSelection,
  batchType,
  batchWire,
  deferred,
} from "./leave-batch-fixture";
import { createLeaveFeature } from "./leave-feature";

const signal = () => new AbortController().signal;
const setup = (response: unknown = batchWire) => {
  const request = vi
    .fn<(input: HttpRequest, signal: AbortSignal) => Promise<unknown>>()
    .mockResolvedValue(response);
  return { request, feature: createLeaveFeature({ request }, { downloadBinary: vi.fn() }) };
};
const creation = () => ({
  ...batchSelection,
  companyId: batchCompany,
  id: batchId,
  expectedPolicyVersion: 4,
  employeeIds: [...batchReviewWire.employeeIds],
  reason: "  September entitlement  ",
});
describe("reviewed leave batch boundary", () => {
  it("acquires historical policy independently of its current head and owns reviewed IDs", async () => {
    const wire = structuredClone(batchReviewWire),
      { request, feature } = setup(wire);
    const result = await feature.reviewBatch.execute(batchAccess, batchSelection, signal());
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value.type).toMatchObject({ version: 4, appliedRevision: 2 });
    wire.employeeIds[0] = batchEmployee(50);
    expect(result.value.employeeIds[0]).toBe(batchEmployee(1));
    expect(Object.isFrozen(result.value.employeeIds)).toBe(true);
    expect(request.mock.calls[0]?.[0].path).toContain(
      `batches/review?kind=ACCRUAL&typeId=${batchType}&period=2026-09`,
    );
  });
  it("discovers policies with operation scope and a bounded code cursor", async () => {
    const { request, feature } = setup({
      items: [{ ...batchReviewWire.type, appliedRevision: 4 }],
      nextCursor: null,
    });
    expect((await feature.loadBatchTypes.execute(batchAccess, "ACCRUAL", "AAA", signal())).ok).toBe(
      true,
    );
    expect(request.mock.calls[0]?.[0].path).toContain(
      "batches/types?kind=ACCRUAL&limit=20&after=AAA",
    );
  });
  it.each(["leave.read", "leave.accrual.post"])(
    "requires %s before accrual I/O",
    async (permission) => {
      const { request, feature } = setup();
      const access = {
        ...batchAccess,
        permissions: batchAccess.permissions.filter((item) => item !== permission),
      };
      expect(
        await feature.startBatch.execute(access, batchOperation, creation(), signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(request).not.toHaveBeenCalled();
    },
  );
  it.each(["2026-00", "2026-13", "2200-01", "2026-1"])(
    "rejects invalid period %s without I/O",
    async (period) => {
      const { request, feature } = setup();
      expect(
        (await feature.reviewBatch.execute(batchAccess, { ...batchSelection, period }, signal()))
          .ok,
      ).toBe(false);
      expect(request).not.toHaveBeenCalled();
    },
  );
  it("requires December for a reviewed year close", async () => {
    const { request, feature } = setup();
    expect(
      (
        await feature.reviewBatch.execute(
          batchAccess,
          { ...batchSelection, kind: "YEAR_CLOSE" },
          signal(),
        )
      ).ok,
    ).toBe(false);
    expect(request).not.toHaveBeenCalled();
  });
  it.each(["duplicate", "count", "future", "effective", "inactive", "manual", "revision"])(
    "rejects incoherent review: %s",
    async (problem) => {
      const wire = structuredClone(batchReviewWire);
      if (problem === "duplicate") wire.employeeIds[1] = wire.employeeIds[0] ?? "";
      if (problem === "count") wire.totalEmployees += 1;
      if (problem === "future") wire.companyDate = "2026-09-30";
      if (problem === "effective") wire.type.effectiveFrom = "2026-10-01";
      if (problem === "inactive") wire.type.active = false;
      if (problem === "manual" && wire.type.accrual) wire.type.accrual.frequency = "MANUAL";
      if (problem === "revision") wire.type.appliedRevision = 5;
      const { feature } = setup(wire);
      expect(
        await feature.reviewBatch.execute(batchAccess, batchSelection, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    },
  );
  it.each(["ACCRUAL", "YEAR_CLOSE"] as const)(
    "posts the reviewed %s inputs and exact receipt",
    async (kind) => {
      const { request, feature } = setup({ id: batchId, version: 0 });
      const input = { ...creation(), kind, period: kind === "ACCRUAL" ? "2026-09" : "2025-12" };
      expect(
        await feature.startBatch.execute(batchAccess, batchOperation, input, signal()),
      ).toEqual({ ok: true, value: { id: batchId, version: 0 } });
      expect(request.mock.calls[0]?.[0]).toEqual({
        path: `/api/v1/companies/${batchCompany}/leave/${kind === "ACCRUAL" ? "accrual" : "year-close"}-batches`,
        method: "POST",
        operationId: batchOperation,
        body: {
          id: batchId,
          typeId: batchType,
          expectedPolicyVersion: 4,
          employeeIds: batchReviewWire.employeeIds,
          reason: "September entitlement",
          ...(kind === "ACCRUAL" ? { month: "2026-09" } : { year: 2025 }),
        },
      });
    },
  );
  it("detaches caller-owned IDs before acquisition and normalizes UUID casing", async () => {
    const pending = deferred<unknown>(),
      { request, feature } = setup();
    request.mockReturnValue(pending.promise);
    const input = {
      ...creation(),
      id: batchId.toUpperCase() as LeaveBatchId,
      typeId: batchType.toUpperCase(),
    };
    const operation = feature.startBatch.execute(batchAccess, batchOperation, input, signal());
    input.employeeIds.splice(0);
    expect(request.mock.calls[0]?.[0].body).toMatchObject({
      id: batchId,
      typeId: batchType,
      employeeIds: batchReviewWire.employeeIds,
    });
    pending.resolve({ id: batchId, version: 0 });
    expect((await operation).ok).toBe(true);
  });
  it.each(["duplicate", "oversized", "version", "reason", "company", "operation"])(
    "rejects invalid command: %s",
    async (problem) => {
      const { request, feature } = setup(),
        input = creation();
      if (problem === "duplicate") input.employeeIds[1] = input.employeeIds[0] ?? "";
      if (problem === "oversized")
        input.employeeIds = Array.from({ length: 5001 }, (_, index) => batchEmployee(index));
      if (problem === "version") input.expectedPolicyVersion = -1;
      if (problem === "reason") input.reason = " ";
      if (problem === "company") input.companyId = batchEmployee(9) as CompanyId;
      expect(
        (
          await feature.startBatch.execute(
            batchAccess,
            problem === "operation" ? (batchId.toUpperCase() as OperationId) : batchOperation,
            input,
            signal(),
          )
        ).ok,
      ).toBe(false);
      expect(request).not.toHaveBeenCalled();
    },
  );
  it("retains outcome labels and allows terminal worker recovery on an incomplete batch", async () => {
    const { feature } = setup();
    const result = await feature.loadBatch.execute(batchAccess, batchId, null, signal());
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value).toMatchObject({
      batch: { status: "RUNNING" },
      job: { status: "FAILED" },
      actions: ["RESUME"],
    });
    expect(result.value.results.items[0]?.employee.name).toBe("Alex Employee");
    expect(Object.isFrozen(result.value.results.items[0]?.parameters)).toBe(true);
  });
  it.each([
    "job",
    "counts",
    "progress",
    "attempt",
    "ordinal",
    "employee",
    "disposition",
    "completed",
    "action",
  ])("rejects incoherent details: %s", async (problem) => {
    const wire = structuredClone(batchWire);
    if (problem === "job") wire.job.jobId = batchEmployee(9);
    if (problem === "counts") wire.counts.failed += 1;
    if (problem === "progress") wire.job.completedItems = 0;
    if (problem === "attempt" && wire.attempts[0]) wire.attempts[0].baseCompleted = 1;
    if (problem === "ordinal" && wire.results.items[0]) wire.results.items[0].ordinal = 2;
    if (problem === "employee")
      wire.employees.push({ id: batchEmployee(9), name: null, number: null });
    if (problem === "disposition" && wire.results.items[0]) wire.results.items[0].resourceId = null;
    if (problem === "completed") wire.batch.status = "COMPLETED";
    if (problem === "action") wire.job.status = "RUNNING";
    const { feature } = setup(wire);
    expect(await feature.loadBatch.execute(batchAccess, batchId, null, signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });
  it("accepts 5,000 employees plus the final metadata checkpoint", async () => {
    const wire = structuredClone(batchWire);
    wire.batch.totalEmployees = 5000;
    wire.counts = { applied: 0, unchanged: 0, skipped: 0, failed: 0, completed: 0 };
    wire.job = {
      ...wire.job,
      status: "QUEUED",
      completedItems: 0,
      totalItems: 5001,
      failureCode: null,
    };
    wire.results = { items: [], nextCursor: null };
    wire.employees = [];
    wire.availableActions = [];
    expect(
      (await setup(wire).feature.loadBatch.execute(batchAccess, batchId, null, signal())).ok,
    ).toBe(true);
  });
  it("rejects a missing next cursor while retaining bounded ordinal pages", async () => {
    const wire = structuredClone(batchWire),
      sample = wire.results.items[0];
    if (!sample) throw Error("Missing fixture");
    wire.batch.totalEmployees = 25;
    wire.counts = { applied: 25, unchanged: 0, skipped: 0, failed: 0, completed: 25 };
    wire.job = { ...wire.job, completedItems: 25, totalItems: 26 };
    wire.results.items = Array.from({ length: 20 }, (_, index) => ({
      ...sample,
      ordinal: index + 1,
      employeeId: batchEmployee(index + 1),
    }));
    wire.employees = wire.results.items.map((row) => ({
      id: row.employeeId,
      name: null,
      number: null,
    }));
    const { request, feature } = setup(wire);
    expect((await feature.loadBatch.execute(batchAccess, batchId, null, signal())).ok).toBe(false);
    request.mockResolvedValue({ ...wire, results: { ...wire.results, nextCursor: "20" } });
    expect((await feature.loadBatch.execute(batchAccess, batchId, null, signal())).ok).toBe(true);
  });
  it.each(["-1", "01", "5001", "1.0"])(
    "rejects invalid result cursor %s without I/O",
    async (after) => {
      const { request, feature } = setup();
      expect((await feature.loadBatch.execute(batchAccess, batchId, after, signal())).ok).toBe(
        false,
      );
      expect(request).not.toHaveBeenCalled();
    },
  );
  it("does not accept another command's receipt or an unrelated resumed revision", async () => {
    const { request, feature } = setup({ id: batchEmployee(8), version: 0 });
    expect(
      (await feature.startBatch.execute(batchAccess, batchOperation, creation(), signal())).ok,
    ).toBe(false);
    request.mockResolvedValue({ id: batchId, version: 2 });
    expect(
      (
        await feature.resumeBatch.execute(
          batchAccess,
          batchOperation,
          {
            companyId: batchCompany,
            id: batchId,
            kind: "ACCRUAL",
            expectedVersion: 0,
            reason: "Continue",
          },
          signal(),
        )
      ).ok,
    ).toBe(false);
  });
  it("retains stable server failures and discards technical transport text", async () => {
    const { request, feature } = setup();
    request.mockRejectedValueOnce(
      new HttpResponseError(409, {
        code: "leave_batch_policy_changed",
        parameters: {},
        fields: {},
      }),
    );
    expect(await feature.loadBatch.execute(batchAccess, batchId, null, signal())).toMatchObject({
      ok: false,
      failure: { code: "leave_batch_policy_changed" },
    });
    request.mockRejectedValueOnce(new TypeError("private transport detail"));
    expect(await feature.loadBatch.execute(batchAccess, batchId, null, signal())).toMatchObject({
      ok: false,
      failure: { code: "connection_unavailable" },
    });
  });
  it.each(["result", "failure"])("propagates cancellation before a late %s", async (late) => {
    const pending = deferred<unknown>(),
      abort = new AbortController(),
      { request, feature } = setup();
    request.mockReturnValue(pending.promise);
    const operation = feature.loadBatch.execute(batchAccess, batchId, null, abort.signal);
    const assertion = expect(operation).rejects.toMatchObject({ name: "AbortError" });
    abort.abort();
    if (late === "result") pending.resolve(batchWire);
    else pending.reject(new TypeError("late"));
    await assertion;
  });
});
