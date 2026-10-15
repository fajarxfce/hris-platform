import { describe, expect, it, vi } from "vitest";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import {
  accrualScheduleId as id,
  accrualReview as review,
  accrualSelection as selection,
} from "../../di/leave-accrual-schedule-fixture";
import {
  batchAccess as access,
  deferred,
  batchOperation as operation,
} from "../../di/leave-batch-fixture";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveAccrualScheduleStartController } from "./leave-accrual-schedule-start-controller";

const fixture = () => {
  const load = vi
    .fn<LeaveUseCases["reviewAccrualSchedule"]["execute"]>()
    .mockResolvedValue(success(review));
  const command = vi
    .fn<LeaveUseCases["scheduleAccrual"]["execute"]>()
    .mockResolvedValue(success({ id, version: 0 }));
  const next = vi.fn<() => string>().mockReturnValueOnce(operation).mockReturnValueOnce(id);
  const controller = new LeaveAccrualScheduleStartController(
    { reviewAccrualSchedule: { execute: load }, scheduleAccrual: { execute: command } },
    access,
    next,
  );
  return { load, command, next, controller };
};
describe("schedule command ownership", () => {
  it("requires explicit review and freezes its exact selection before submission", async () => {
    const f = fixture();
    f.controller.activate();
    await f.controller.save("Premature");
    expect(f.load).not.toHaveBeenCalled();
    expect(f.command).not.toHaveBeenCalled();
    await f.controller.prepare(selection);
    await f.controller.save("  Reviewed schedule  ");
    expect(f.command.mock.lastCall?.slice(0, 3)).toEqual([
      access,
      operation,
      {
        ...selection,
        id,
        companyId: access.companyId,
        expectedPolicyVersion: 4,
        employeeIds: review.employeeIds,
        reason: "Reviewed schedule",
      },
    ]);
    expect(Object.isFrozen(f.command.mock.lastCall?.[2].employeeIds)).toBe(true);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      scheduleId: id,
      review: null,
    });
    f.controller.deactivate();
  });
  it("owns the selection while review is pending", async () => {
    const f = fixture(),
      held = deferred<Result<typeof review>>(),
      selected = { ...selection };
    f.load.mockReturnValueOnce(held.promise);
    f.controller.activate();
    const pending = f.controller.prepare(selected);
    selected.periods = 12;
    expect(f.load.mock.lastCall?.[1].periods).toBe(3);
    held.resolve(success(review));
    await pending;
    f.controller.deactivate();
  });
  it("rejects late failed reads after disposal and reopening", async () => {
    const f = fixture(),
      held = deferred<Result<typeof review>>();
    f.load.mockReturnValueOnce(held.promise);
    f.controller.activate();
    const pending = f.controller.prepare(selection),
      signal = f.load.mock.lastCall?.[2];
    f.controller.deactivate();
    f.controller.activate();
    await f.controller.prepare(selection);
    held.resolve(failed("access_denied"));
    await pending;
    expect(signal?.aborted).toBe(true);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "reviewing", review, failure: null });
    f.controller.deactivate();
  });
  it("suspends read acquisition and reacquires after workspace admission", async () => {
    const f = fixture(),
      held = deferred<Result<typeof review>>();
    f.load.mockReturnValueOnce(held.promise);
    f.controller.activate();
    const pending = f.controller.prepare(selection),
      signal = f.load.mock.lastCall?.[2];
    f.controller.setVisible(false);
    await f.controller.prepare(selection);
    await f.controller.save("Hidden");
    expect(f.load).toHaveBeenCalledTimes(1);
    expect(f.command).not.toHaveBeenCalled();
    f.controller.setVisible(true);
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
    held.reject(Error("Late private read"));
    await pending;
    expect(signal?.aborted).toBe(true);
    expect(f.controller.getSnapshot()).toMatchObject({ review, failure: null });
    f.controller.deactivate();
  });
  it("renews a review after MFA without manufacturing a command", async () => {
    const f = fixture();
    f.load.mockResolvedValueOnce(failed("mfa_required"));
    f.controller.activate();
    await f.controller.prepare(selection);
    expect(f.controller.getSnapshot().stage).toBe("unavailable");
    f.controller.setVisible(false);
    f.controller.setVisible(true);
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
    expect(f.command).not.toHaveBeenCalled();
    expect(f.next).not.toHaveBeenCalled();
    f.controller.deactivate();
  });
  it("keeps one ambiguous command through MFA and conflicts", async () => {
    const f = fixture();
    f.command
      .mockResolvedValueOnce(failed("network_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("stale_policy_version"));
    f.controller.activate();
    await f.controller.prepare(selection);
    await f.controller.save("Reviewed schedule");
    const first = f.command.mock.lastCall?.slice(0, 3);
    f.controller.refresh();
    f.controller.revise();
    await f.controller.save("Replacement");
    await f.controller.retry();
    f.controller.setVisible(false);
    f.controller.setVisible(true);
    expect(f.load).toHaveBeenCalledTimes(1);
    expect(f.command).toHaveBeenCalledTimes(2);
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    await f.controller.retry();
    for (const call of f.command.mock.calls) expect(call.slice(0, 3)).toEqual(first);
    expect(f.next).toHaveBeenCalledTimes(2);
    expect(f.controller.getSnapshot().stage).toBe("saved");
    f.controller.deactivate();
  });
  it("an interrupted write remains unconfirmed and needs explicit original-command recovery", async () => {
    const f = fixture(),
      held = deferred<Result<MutationReceipt>>();
    f.command.mockReturnValueOnce(held.promise);
    f.controller.activate();
    await f.controller.prepare(selection);
    const pending = f.controller.save("Reviewed schedule"),
      signal = f.command.mock.lastCall?.[3],
      first = f.command.mock.lastCall?.slice(0, 3);
    f.controller.setVisible(false);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      review: null,
      operationId: operation,
    });
    held.resolve(success({ id, version: 0 }));
    await pending;
    f.controller.setVisible(true);
    expect(signal?.aborted).toBe(true);
    expect(f.command).toHaveBeenCalledTimes(1);
    await f.controller.retry();
    expect(f.command.mock.lastCall?.slice(0, 3)).toEqual(first);
    expect(f.controller.getSnapshot().stage).toBe("saved");
    f.controller.deactivate();
  });
  it("prevents duplicate sends while a command is pending", async () => {
    const f = fixture(),
      held = deferred<Result<MutationReceipt>>();
    f.command.mockReturnValueOnce(held.promise);
    f.controller.activate();
    await f.controller.prepare(selection);
    const pending = f.controller.save("Reviewed schedule");
    await f.controller.save("Again");
    await f.controller.retry();
    f.controller.revise();
    f.controller.refresh();
    expect(f.command).toHaveBeenCalledTimes(1);
    held.resolve(success({ id, version: 0 }));
    await pending;
    f.controller.deactivate();
  });
  it.each(["stale_policy_version", "leave_schedule_frequency_changed", "leave_batch_active"])(
    "a first rejected %s command returns to explicit review",
    async (code) => {
      const f = fixture();
      f.command.mockResolvedValueOnce(failed(code));
      f.controller.activate();
      await f.controller.prepare(selection);
      await f.controller.save("Reviewed schedule");
      expect(f.controller.getSnapshot()).toMatchObject({ stage: "conflict", operationId: null });
      f.controller.revise();
      expect(f.controller.getSnapshot()).toMatchObject({ stage: "editing", review: null });
      f.controller.deactivate();
    },
  );
  it("does not restore private review after confirmed scope loss", async () => {
    const f = fixture();
    f.load.mockResolvedValue(failed("company_access_denied"));
    f.controller.activate();
    await f.controller.prepare(selection);
    f.controller.setVisible(false);
    f.controller.setVisible(true);
    expect(f.load).toHaveBeenCalledTimes(1);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", review: null });
    f.controller.deactivate();
  });
  it("clears command ownership when the workspace is replaced", async () => {
    const f = fixture();
    f.command.mockResolvedValueOnce(failed("network_unavailable"));
    f.controller.activate();
    await f.controller.prepare(selection);
    await f.controller.save("Reviewed schedule");
    f.controller.deactivate();
    f.controller.activate();
    await f.controller.retry();
    expect(f.command).toHaveBeenCalledTimes(1);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "editing", operationId: null });
    f.controller.deactivate();
  });
});
