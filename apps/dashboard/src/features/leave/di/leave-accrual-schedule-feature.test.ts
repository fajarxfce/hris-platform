import { describe, expect, it, vi } from "vitest";
import type { HttpRequest } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import {
  accrualCreation,
  accrualReviewWire,
  accrualScheduleId,
  accrualSelection,
  accrualWire,
} from "./leave-accrual-schedule-fixture";
import {
  batchAccess,
  batchCompany,
  batchEmployee,
  batchOperation,
  batchType,
  deferred,
} from "./leave-batch-fixture";
import { createLeaveFeature } from "./leave-feature";

const signal = () => new AbortController().signal;
const setup = (response: unknown = accrualWire) => {
  const request = vi
    .fn<(input: HttpRequest, signal: AbortSignal) => Promise<unknown>>()
    .mockResolvedValue(response);
  return { request, feature: createLeaveFeature({ request }, { downloadBinary: vi.fn() }) };
};
describe("accrual schedule boundaries", () => {
  it("acquires bounded future policies and owns all nested review values", async () => {
    const wire = structuredClone(accrualReviewWire),
      f = setup(wire);
    const result = await f.feature.reviewAccrualSchedule.execute(
      batchAccess,
      accrualSelection,
      signal(),
    );
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(f.request.mock.calls[0]?.[0].path).toContain(
      `accrual-schedules/review?typeId=${batchType}&firstPeriod=2026-10&periods=3`,
    );
    expect(result.value.periods[0]?.type).toMatchObject({ version: 4, appliedRevision: 2 });
    wire.employeeIds[0] = batchEmployee(77);
    wire.periods[0]?.type.allowedContracts.push("FIXED_TERM");
    expect(result.value.employeeIds[0]).toBe(batchEmployee(1));
    expect(result.value.periods[0]?.type.allowedContracts).toEqual(["PERMANENT"]);
    expect(Object.isFrozen(result.value.periods)).toBe(true);
    expect(Object.isFrozen(result.value.selection)).toBe(true);
  });
  it.each(["leave.read", "leave.accrual.post"])(
    "requires %s before review or command I/O",
    async (permission) => {
      const f = setup(),
        access = {
          ...batchAccess,
          permissions: batchAccess.permissions.filter((item) => item !== permission),
        };
      expect(
        await f.feature.reviewAccrualSchedule.execute(access, accrualSelection, signal()),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(
        await f.feature.scheduleAccrual.execute(
          access,
          batchOperation,
          accrualCreation(),
          signal(),
        ),
      ).toMatchObject({ ok: false, failure: { code: "access_denied" } });
      expect(f.request).not.toHaveBeenCalled();
    },
  );
  it.each([0, 13, 1.5, Number.POSITIVE_INFINITY])(
    "rejects invalid period count %s without I/O",
    async (periods) => {
      const f = setup();
      expect(
        (
          await f.feature.reviewAccrualSchedule.execute(
            batchAccess,
            { ...accrualSelection, periods },
            signal(),
          )
        ).ok,
      ).toBe(false);
      expect(f.request).not.toHaveBeenCalled();
    },
  );
  it.each(["2026-00", "2026-13", "2026-1", "2200-01"])(
    "rejects invalid first period %s",
    async (firstPeriod) => {
      const f = setup();
      expect(
        (
          await f.feature.reviewAccrualSchedule.execute(
            batchAccess,
            { ...accrualSelection, firstPeriod },
            signal(),
          )
        ).ok,
      ).toBe(false);
      expect(f.request).not.toHaveBeenCalled();
    },
  );
  it.each([
    "period-count",
    "period-order",
    "foreign-type",
    "frequency",
    "head",
    "revision",
    "effective",
    "duplicate-employee",
    "inactive",
    "invalid-date",
  ])("rejects an incoherent review: %s", async (problem) => {
    const wire = structuredClone(accrualReviewWire),
      first = wire.periods[0],
      last = wire.periods[2];
    if (!first || !last) throw Error("Missing fixture");
    if (problem === "period-count") wire.periods.pop();
    if (problem === "period-order") last.period = "2027-12";
    if (problem === "foreign-type") last.type.id = batchEmployee(9);
    if (problem === "frequency") wire.frequency = "ANNUAL";
    if (problem === "head") last.type.version += 1;
    if (problem === "revision") last.type.appliedRevision = 5;
    if (problem === "effective") first.type.effectiveFrom = "2026-11-01";
    if (problem === "duplicate-employee") wire.employeeIds[1] = wire.employeeIds[0] ?? "";
    if (problem === "inactive") last.type.active = false;
    if (problem === "invalid-date") last.dueAt = "2026-13-01T00:00:00Z";
    expect(
      await setup(wire).feature.reviewAccrualSchedule.execute(
        batchAccess,
        accrualSelection,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("supports annual periods while preserving the eligibility month", async () => {
    const wire = structuredClone(accrualReviewWire);
    wire.frequency = "ANNUAL";
    wire.periods.forEach((row, index) => {
      row.period = `${2026 + index}-10`;
      row.dueAt = `${2026 + index}-09-30T17:00:00Z`;
      if (row.type.accrual) row.type.accrual.frequency = "ANNUAL";
    });
    const result = await setup(wire).feature.reviewAccrualSchedule.execute(
      batchAccess,
      accrualSelection,
      signal(),
    );
    expect(result.ok).toBe(true);
    if (result.ok)
      expect(result.value.periods.map((row) => row.period)).toEqual([
        "2026-10",
        "2027-10",
        "2028-10",
      ]);
  });
  it("captures the original cohort and exact command receipt before waiting", async () => {
    const held = deferred<unknown>(),
      f = setup();
    f.request.mockReturnValue(held.promise);
    const employees = [...accrualReviewWire.employeeIds],
      input = { ...accrualCreation(), employeeIds: employees };
    const result = f.feature.scheduleAccrual.execute(batchAccess, batchOperation, input, signal());
    employees[0] = batchEmployee(77);
    held.resolve({ id: accrualScheduleId, version: 0 });
    expect(await result).toEqual({ ok: true, value: { id: accrualScheduleId, version: 0 } });
    expect(f.request.mock.calls[0]?.[0]).toEqual({
      path: `/api/v1/companies/${batchCompany}/leave/accrual-schedules`,
      method: "POST",
      operationId: batchOperation,
      body: {
        ...accrualSelection,
        id: accrualScheduleId,
        employeeIds: accrualReviewWire.employeeIds,
        expectedPolicyVersion: 4,
        reason: "Reviewed entitlement schedule",
      },
    });
  });
  it("rejects foreign command scope, malformed operations and duplicated employees", async () => {
    const f = setup();
    for (const input of [
      { ...accrualCreation(), companyId: batchEmployee(8) as CompanyId },
      { ...accrualCreation(), employeeIds: [batchEmployee(1), batchEmployee(1)] },
    ])
      expect(
        (await f.feature.scheduleAccrual.execute(batchAccess, batchOperation, input, signal())).ok,
      ).toBe(false);
    expect(
      (
        await f.feature.scheduleAccrual.execute(
          batchAccess,
          "wrong" as OperationId,
          accrualCreation(),
          signal(),
        )
      ).ok,
    ).toBe(false);
    expect(f.request).not.toHaveBeenCalled();
  });
  it.each([
    { id: batchEmployee(9), version: 0 },
    { id: accrualScheduleId, version: 1 },
  ])("rejects an unrelated receipt: %s", async (receipt) => {
    expect(
      await setup(receipt).feature.scheduleAccrual.execute(
        batchAccess,
        batchOperation,
        accrualCreation(),
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("reads current jobs without dropping the retained schedule", async () => {
    const wire = structuredClone(accrualWire),
      first = wire.periods[0];
    if (!first) throw Error("Missing fixture");
    first.batch.jobId = batchEmployee(90);
    first.batch.version = 2;
    first.completedEmployees = 1;
    first.jobStatus = "RUNNING";
    const result = await setup(wire).feature.loadAccrualSchedule.execute(
      batchAccess,
      accrualScheduleId,
      signal(),
    );
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    wire.schedule.reason = "Changed";
    expect(result.value.schedule.reason).toBe("Reviewed entitlement schedule");
    expect(result.value.periods[0]?.batch.jobId).toBe(batchEmployee(90));
    expect(Object.isFrozen(result.value.periods[0]?.batch.policy.allowedContracts)).toBe(true);
  });
  it.each([
    "scope",
    "count",
    "duplicate",
    "job",
    "period",
    "policy",
    "cohort",
    "actor",
    "time",
    "early",
    "completed",
    "progress",
  ])("rejects incoherent retained detail: %s", async (problem) => {
    const wire = structuredClone(accrualWire),
      first = wire.periods[0],
      second = wire.periods[1];
    if (!first || !second) throw Error("Missing fixture");
    if (problem === "scope") wire.schedule.id = batchEmployee(9);
    if (problem === "count") wire.periods.pop();
    if (problem === "duplicate") second.batch.id = first.batch.id;
    if (problem === "job") second.batch.jobId = first.batch.jobId;
    if (problem === "period") second.batch.period = "2028-11";
    if (problem === "policy") second.batch.policyVersion = 5;
    if (problem === "cohort") second.batch.totalEmployees = 4;
    if (problem === "actor") second.batch.actorId = batchEmployee(7);
    if (problem === "time") second.batch.createdAt = "2026-10-15T02:00:00Z";
    if (problem === "early") first.scheduledFor = "2026-01-01T00:00:00Z";
    if (problem === "completed") first.jobStatus = "SUCCEEDED";
    if (problem === "progress") first.completedEmployees = 4;
    expect(
      await setup(wire).feature.loadAccrualSchedule.execute(
        batchAccess,
        accrualScheduleId,
        signal(),
      ),
    ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
  });
  it("keeps history pages ascending and checks the returned cursor", async () => {
    const items = Array.from({ length: 20 }, (_, index) => ({
      ...accrualWire.schedule,
      id: `a0000000-abcd-4000-8000-${String(index + 1).padStart(12, "0")}`,
    }));
    const f = setup({ items, nextCursor: items.at(-1)?.id });
    expect((await f.feature.loadAccrualSchedules.execute(batchAccess, null, signal())).ok).toBe(
      true,
    );
    expect(f.request.mock.calls[0]?.[0].path).toContain("accrual-schedules?limit=20");
    f.request.mockResolvedValue({ items: [...items].reverse(), nextCursor: null });
    expect((await f.feature.loadAccrualSchedules.execute(batchAccess, null, signal())).ok).toBe(
      false,
    );
    f.request.mockResolvedValue({ items, nextCursor: items[0]?.id });
    expect((await f.feature.loadAccrualSchedules.execute(batchAccess, null, signal())).ok).toBe(
      false,
    );
  });
  it("preserves safe server failure codes and never technical text", async () => {
    const f = setup();
    f.request.mockRejectedValue(
      new HttpResponseError(409, {
        code: "job_queue_full",
        parameters: {},
        fields: {},
        detail: "PRIVATE SQL",
      }),
    );
    const result = await f.feature.scheduleAccrual.execute(
      batchAccess,
      batchOperation,
      accrualCreation(),
      signal(),
    );
    expect(result).toMatchObject({ ok: false, failure: { code: "job_queue_full" } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE SQL");
  });
  it("propagates cancellation before and after acquisition", async () => {
    const f = setup(accrualReviewWire),
      controller = new AbortController();
    controller.abort();
    expect(() =>
      f.feature.reviewAccrualSchedule.execute(batchAccess, accrualSelection, controller.signal),
    ).toThrow();
    expect(f.request).not.toHaveBeenCalled();
    const held = deferred<unknown>(),
      next = new AbortController();
    f.request.mockReturnValue(held.promise);
    const result = f.feature.reviewAccrualSchedule.execute(
      batchAccess,
      accrualSelection,
      next.signal,
    );
    next.abort();
    held.resolve(accrualReviewWire);
    await expect(result).rejects.toThrow();
  });
});
