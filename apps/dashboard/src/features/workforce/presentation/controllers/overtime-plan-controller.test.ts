import { afterEach, describe, expect, it, vi } from "vitest";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { company, employeeId, identifier, overtimeDate } from "../../di/overtime-fixture";
import { overtimePlanningFixture } from "../../di/overtime-planning-fixture";
import type { OvertimePlanningReview } from "../../domain/entities/overtime-planning-review";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { OvertimePlanController } from "./overtime-plan-controller";

function deferred<T>() {
  let resolve!: (value: T) => void, reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
const controllers: OvertimePlanController[] = [];
afterEach(() => {
  for (const controller of controllers.splice(0)) controller.deactivate();
});
const input = {
  startsAt: "2026-10-12T07:00:00Z",
  endsAt: "2026-10-12T10:00:00Z",
  breakMinutes: 15,
};
function fixture(active = true, permissions = ["overtime.manage"]) {
  const record = structuredClone(overtimePlanningFixture());
  const review = vi
    .fn<WorkforceUseCases["reviewOvertimePlan"]["execute"]>()
    .mockResolvedValue(success(record));
  const plan = vi
    .fn<WorkforceUseCases["planOvertimeRequest"]["execute"]>()
    .mockImplementation(async (_access, _operation, id) => success({ id, version: 0 }));
  let sequence = 1000;
  const next = vi.fn(() => identifier(++sequence));
  const controller = new OvertimePlanController(
    { reviewOvertimePlan: { execute: review }, planOvertimeRequest: { execute: plan } },
    { companyId: company, permissions },
    overtimeDate,
    next,
  );
  controllers.push(controller);
  if (active) controller.activate();
  return { controller, record, review, plan, next };
}

describe("overtime plan ownership", () => {
  it("requires an active authorized route and an explicit employee selection before acquisition", async () => {
    const f = fixture(false);
    await f.controller.selectEmployee(employeeId);
    await f.controller.submit(input, "Work");
    expect(f.review).not.toHaveBeenCalled();
    f.controller.activate();
    f.controller.activate();
    await f.controller.refresh();
    expect(f.review).not.toHaveBeenCalled();
    await f.controller.selectEmployee(employeeId);
    expect(f.controller.getSnapshot().stage).toBe("reviewing");
    expect(f.review).toHaveBeenCalledOnce();
    const denied = fixture(true, ["overtime.self.manage"]);
    await denied.controller.selectEmployee(employeeId);
    expect(denied.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      failure: { code: "access_denied" },
    });
    expect(denied.review).not.toHaveBeenCalled();
  });

  it("changing the work date cancels an obsolete review and removes the previous employee", async () => {
    const f = fixture();
    await f.controller.selectEmployee(employeeId);
    const held = deferred<Result<OvertimePlanningReview>>();
    f.review.mockReturnValueOnce(held.promise);
    const pending = f.controller.refresh();
    const oldSignal = f.review.mock.lastCall?.[3];
    expect(f.controller.getSnapshot().review).toBeNull();
    f.controller.changeDate("2026-10-14");
    expect(oldSignal?.aborted).toBe(true);
    held.resolve(success(f.record));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "choosing",
      workDate: "2026-10-14",
      employeeId: null,
      review: null,
    });
    f.controller.changeDate("2026-02-30");
    expect(f.controller.getSnapshot().failure?.code).toBe("overtime_date_out_of_range");
    expect(f.plan).not.toHaveBeenCalled();
  });

  it("a newer selection cannot be overwritten by a late failure from the previous review", async () => {
    const f = fixture(),
      held = deferred<Result<OvertimePlanningReview>>();
    f.review.mockReturnValueOnce(held.promise);
    const first = f.controller.selectEmployee(employeeId);
    const oldSignal = f.review.mock.lastCall?.[3];
    const next = { ...f.record, employee: { ...f.record.employee, legalName: "Current Employee" } };
    f.review.mockResolvedValueOnce(success(next));
    await f.controller.selectEmployee(identifier(99));
    held.reject(new Error("Late read"));
    await first;
    expect(oldSignal?.aborted).toBe(true);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "reviewing",
      review: next,
      failure: null,
    });
  });

  it("freezes one command and prevents changes or duplicate submission during delivery", async () => {
    const f = fixture();
    await f.controller.selectEmployee(employeeId);
    const held = deferred<Result<MutationReceipt>>();
    f.plan.mockReturnValueOnce(held.promise);
    const fields = { ...input };
    const pending = f.controller.submit(fields, "Original reason");
    fields.breakMinutes = 100;
    Object.assign(f.record.employee, { version: 9, legalName: "Changed name" });
    const original = f.plan.mock.calls[0];
    expect(original?.[3]).toMatchObject({ employmentVersion: 3, revision: "a".repeat(64) });
    expect(original?.[3]).not.toHaveProperty("employee");
    expect(Object.isFrozen(original?.[3])).toBe(true);
    expect(Object.isFrozen(original?.[4])).toBe(true);
    await f.controller.submit(input, "Replacement");
    await f.controller.selectEmployee(identifier(88));
    f.controller.changeDate("2026-10-14");
    await f.controller.refresh();
    await f.controller.retry();
    expect(f.plan).toHaveBeenCalledOnce();
    expect(f.review).toHaveBeenCalledOnce();
    expect(f.controller.getSnapshot().workDate).toBe(overtimeDate);
    held.resolve(failed("request_timeout"));
    await pending;
    await f.controller.retry();
    expect(f.plan.mock.calls[1]?.slice(0, 6)).toEqual(original?.slice(0, 6));
    expect(f.plan.mock.calls[1]?.[4].breakMinutes).toBe(15);
    expect(f.next).toHaveBeenCalledTimes(2);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      review: null,
      employeeId: null,
      receipt: { id: original?.[2], version: 0 },
    });
  });

  it("retains an unknown command through denied and stale replies without reopening private facts", async () => {
    const f = fixture();
    await f.controller.selectEmployee(employeeId);
    f.plan
      .mockResolvedValueOnce(failed("request_timeout"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("overtime_planning_changed"));
    await f.controller.submit(input, "Original");
    await f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      review: null,
      failure: { code: "mfa_required" },
    });
    await f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      review: null,
      failure: { code: "overtime_planning_changed" },
    });
    const held = deferred<Result<MutationReceipt>>();
    f.plan.mockReturnValueOnce(held.promise);
    const pending = f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "submitting",
      review: null,
      failure: null,
    });
    await f.controller.refresh();
    await f.controller.selectEmployee(identifier(88));
    expect(f.review).toHaveBeenCalledOnce();
    held.resolve(success({ id: f.plan.mock.calls[0]?.[2] ?? "", version: 0 }));
    await pending;
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledTimes(2);
    for (const call of f.plan.mock.calls)
      expect(call.slice(0, 6)).toEqual(f.plan.mock.calls[0]?.slice(0, 6));
  });

  it("a definite rejection requires explicit review before a changed-calendar resubmission", async () => {
    const f = fixture();
    await f.controller.selectEmployee(employeeId);
    f.plan
      .mockResolvedValueOnce(failed("invalid_overtime_interval"))
      .mockResolvedValueOnce(failed("overtime_planning_changed"));
    await f.controller.submit(input, "Work");
    expect(f.controller.getSnapshot().stage).toBe("reviewing");
    await f.controller.submit(input, "Work");
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.submit(input, "No review");
    await f.controller.retry();
    expect(f.plan).toHaveBeenCalledTimes(2);
    f.review.mockResolvedValueOnce(success({ ...f.record, revision: "b".repeat(64) }));
    await f.controller.refresh();
    expect(f.plan).toHaveBeenCalledTimes(2);
    await f.controller.submit(input, "Revised work");
    expect(f.plan.mock.lastCall?.[3].revision).toBe("b".repeat(64));
    expect(new Set(f.plan.mock.calls.map((call) => call[1])).size).toBe(3);
  });

  it("a denied read stays hidden throughout pending and failed recovery", async () => {
    const f = fixture();
    await f.controller.selectEmployee(employeeId);
    f.review.mockResolvedValueOnce(failed("access_denied"));
    await f.controller.refresh();
    expect(f.controller.getSnapshot().review).toBeNull();
    const held = deferred<Result<OvertimePlanningReview>>();
    f.review.mockReturnValueOnce(held.promise);
    const pending = f.controller.refresh();
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "loading", review: null });
    held.reject(new Error("Failed recovery"));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", review: null });
    await f.controller.refresh();
    expect(f.controller.getSnapshot().review).not.toBeNull();
  });

  it.each(["read", "write"] as const)(
    "disposal cancels a pending %s and releases the command without publishing late results",
    async (kind) => {
      const f = fixture();
      await f.controller.selectEmployee(employeeId);
      const held = deferred<never>();
      if (kind === "read") f.review.mockReturnValueOnce(held.promise);
      else f.plan.mockReturnValueOnce(held.promise);
      const pending = kind === "read" ? f.controller.refresh() : f.controller.submit(input, "Work");
      const abort = kind === "read" ? f.review.mock.lastCall?.[3] : f.plan.mock.lastCall?.[6];
      f.controller.deactivate();
      expect(abort?.aborted).toBe(true);
      const after = f.controller.getSnapshot();
      held.reject(new Error("Late result"));
      await pending;
      expect(f.controller.getSnapshot()).toBe(after);
      await f.controller.retry();
      f.controller.activate();
      expect(f.controller.getSnapshot().stage).toBe("choosing");
    },
  );
});
