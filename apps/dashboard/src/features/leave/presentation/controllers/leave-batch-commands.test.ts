import { describe, expect, it, vi } from "vitest";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import {
  batchAccess as access,
  deferred,
  batchDetails as details,
  batchId as id,
  batchOperation as operationId,
  batchReview as review,
  batchSelection as selection,
} from "../../di/leave-batch-fixture";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveBatchResumeController } from "./leave-batch-resume-controller";
import { LeaveBatchStartController } from "./leave-batch-start-controller";

function startFixture() {
  const load = vi.fn<LeaveUseCases["reviewBatch"]["execute"]>().mockResolvedValue(success(review));
  const command = vi
    .fn<LeaveUseCases["startBatch"]["execute"]>()
    .mockResolvedValue(success({ id, version: 0 }));
  const next = vi.fn<() => string>().mockReturnValueOnce(operationId).mockReturnValueOnce(id);
  const controller = new LeaveBatchStartController(
    { reviewBatch: { execute: load }, startBatch: { execute: command } },
    access,
    "ACCRUAL",
    next,
  );
  return { load, command, next, controller };
}
function resumeFixture() {
  const load = vi.fn<LeaveUseCases["loadBatch"]["execute"]>().mockResolvedValue(success(details));
  const command = vi
    .fn<LeaveUseCases["resumeBatch"]["execute"]>()
    .mockResolvedValue(success({ id, version: 1 }));
  const next = vi.fn<() => string>().mockReturnValue(operationId);
  const controller = new LeaveBatchResumeController(
    { execute: load },
    { execute: command },
    access,
    id,
    next,
  );
  return { load, command, next, controller };
}
describe("leave batch authorization and immutable command ownership", () => {
  it("acquires only after an explicit selection and captures reviewed IDs and the policy head", async () => {
    const f = startFixture();
    f.controller.activate();
    expect(f.load).not.toHaveBeenCalled();
    await f.controller.save("Early");
    expect(f.command).not.toHaveBeenCalled();
    await f.controller.prepare(selection);
    await f.controller.save("  September entitlement  ");
    expect(f.command.mock.lastCall?.slice(0, 3)).toEqual([
      access,
      operationId,
      {
        ...selection,
        id,
        companyId: access.companyId,
        expectedPolicyVersion: 4,
        employeeIds: review.employeeIds,
        reason: "September entitlement",
      },
    ]);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "saved", batchId: id, review: null });
    f.controller.deactivate();
  });
  it("a different operation cannot replace the route's chosen kind", async () => {
    const f = startFixture();
    f.controller.activate();
    await f.controller.prepare({ ...selection, kind: "YEAR_CLOSE", period: "2025-12" });
    expect(f.load).not.toHaveBeenCalled();
    f.controller.deactivate();
  });
  it("pending review cancellation rejects obsolete results after reopening", async () => {
    const f = startFixture(),
      held = deferred<Result<typeof review>>();
    f.load.mockReturnValueOnce(held.promise);
    f.controller.activate();
    const old = f.controller.prepare(selection),
      signal = f.load.mock.lastCall?.[2];
    f.controller.deactivate();
    f.controller.activate();
    await f.controller.prepare(selection);
    held.resolve(failed("access_denied"));
    await old;
    expect(signal?.aborted).toBe(true);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "reviewing", review, failure: null });
    f.controller.deactivate();
  });
  it("refresh replaces a pending review and ignores its later exception", async () => {
    const f = startFixture(),
      held = deferred<Result<typeof review>>();
    f.load.mockReturnValueOnce(held.promise);
    f.controller.activate();
    const old = f.controller.prepare(selection);
    f.controller.refresh();
    await f.controller.prepare(selection);
    held.reject(new Error("PRIVATE"));
    await old;
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "reviewing", review, failure: null });
    f.controller.deactivate();
  });
  it("owns one pending start and rejects duplicate save or replacement review", async () => {
    const f = startFixture(),
      held = deferred<Result<MutationReceipt>>();
    f.command.mockReturnValueOnce(held.promise);
    f.controller.activate();
    await f.controller.prepare(selection);
    const pending = f.controller.save("Original");
    await f.controller.save("Replacement");
    await f.controller.prepare(selection);
    f.controller.revise();
    f.controller.refresh();
    expect(f.command).toHaveBeenCalledOnce();
    expect(f.load).toHaveBeenCalledOnce();
    expect(f.next).toHaveBeenCalledTimes(2);
    held.resolve(success({ id, version: 0 }));
    await pending;
    f.controller.deactivate();
  });
  it("keeps original start input across response loss, MFA, conflicts and recovery", async () => {
    const f = startFixture();
    f.command
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("stale_version"));
    f.controller.activate();
    await f.controller.prepare(selection);
    await f.controller.save("Original");
    const input = f.command.mock.calls[0]?.[2];
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    await f.controller.retry();
    f.controller.refresh();
    f.controller.revise();
    await f.controller.save("Replacement");
    expect(f.load).toHaveBeenCalledOnce();
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledTimes(2);
    for (const call of f.command.mock.calls) {
      expect(call[1]).toBe(operationId);
      expect(call[2]).toBe(input);
    }
    expect(Object.isFrozen(input?.employeeIds)).toBe(true);
    f.controller.deactivate();
  });
  it("clears denied private review while retaining the original unconfirmed command", async () => {
    const f = startFixture();
    f.command
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("company_access_denied"));
    f.controller.activate();
    await f.controller.prepare(selection);
    await f.controller.save("Original");
    await f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      review: null,
      operationId,
    });
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledTimes(2);
    f.controller.deactivate();
  });
  it.each([
    "leave_batch_policy_changed",
    "leave_batch_active",
    "leave_batch_employee_unavailable",
    "invalid_leave_accrual_policy",
  ])("requires an explicit new review after first rejection %s", async (code) => {
    const f = startFixture();
    f.command.mockResolvedValueOnce(failed(code));
    f.controller.activate();
    await f.controller.prepare(selection);
    await f.controller.save("Original");
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "conflict", operationId: null });
    await f.controller.retry();
    await f.controller.save("Same old review");
    expect(f.command).toHaveBeenCalledOnce();
    f.controller.revise();
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "editing", review: null });
    await f.controller.prepare(selection);
    expect(f.load).toHaveBeenCalledTimes(2);
    f.controller.deactivate();
  });
  it("first definitive rejection erases denied selection without retaining an operation", async () => {
    const f = startFixture();
    f.command.mockResolvedValueOnce(failed("access_denied"));
    f.controller.activate();
    await f.controller.prepare(selection);
    await f.controller.save("Original");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      operationId: null,
    });
    f.controller.deactivate();
  });
  it.each(["result", "failure"])("discarded start ignores a late %s", async (late) => {
    const f = startFixture(),
      held = deferred<Result<MutationReceipt>>();
    f.command.mockReturnValueOnce(held.promise);
    f.controller.activate();
    await f.controller.prepare(selection);
    const pending = f.controller.save("Original"),
      signal = f.command.mock.lastCall?.[3];
    const listener = vi.fn(),
      stop = f.controller.subscribe(listener);
    stop();
    f.controller.deactivate();
    if (late === "result") held.resolve(success({ id, version: 0 }));
    else held.reject(Error("PRIVATE"));
    await pending;
    expect(signal?.aborted).toBe(true);
    expect(listener).not.toHaveBeenCalled();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "editing",
      review: null,
      batchId: null,
    });
  });
  it("identifier generation failure never enters a pending command", async () => {
    const f = startFixture();
    f.next.mockReset().mockImplementation(() => {
      throw Error("PRIVATE");
    });
    f.controller.activate();
    await f.controller.prepare(selection);
    await f.controller.save("Original");
    expect(f.command).not.toHaveBeenCalled();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "reviewing",
      failure: { code: "unexpected_error" },
    });
    f.controller.deactivate();
  });
  it("review scope denial clears selection and missing grants acquire nothing", async () => {
    const f = startFixture();
    f.load.mockResolvedValueOnce(failed("company_module_disabled"));
    f.controller.activate();
    await f.controller.prepare(selection);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", review: null });
    f.controller.deactivate();
    const denied = new LeaveBatchStartController(
      { reviewBatch: { execute: f.load }, startBatch: { execute: f.command } },
      { ...access, permissions: ["leave.read"] },
      "ACCRUAL",
      f.next,
    );
    denied.activate();
    await denied.prepare(selection);
    expect(f.load).toHaveBeenCalledOnce();
    denied.deactivate();
  });
  it("resume uses server availability and the observed batch version", async () => {
    const f = resumeFixture();
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
    await f.controller.save(" Continue remaining ");
    expect(f.command.mock.lastCall?.slice(0, 3)).toEqual([
      access,
      operationId,
      {
        companyId: access.companyId,
        id,
        kind: "ACCRUAL",
        expectedVersion: 0,
        reason: "Continue remaining",
      },
    ]);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "saved", batchId: id });
    f.controller.deactivate();
  });
  it("unavailable resume cannot submit even with the operation grant", async () => {
    const f = resumeFixture();
    f.load.mockResolvedValueOnce(success({ ...details, actions: [] }));
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
    await f.controller.save("Resume");
    expect(f.command).not.toHaveBeenCalled();
    expect(f.next).not.toHaveBeenCalled();
    f.controller.deactivate();
  });
  it("resume has one original command across uncertain delivery, access denial and conflict", async () => {
    const f = resumeFixture();
    f.command
      .mockRejectedValueOnce(Error("PRIVATE"))
      .mockResolvedValueOnce(failed("access_denied"))
      .mockResolvedValueOnce(failed("stale_version"));
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
    await f.controller.save("Original");
    const input = f.command.mock.calls[0]?.[2];
    await f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", review: null });
    await f.controller.refresh();
    await f.controller.save("Replacement");
    await f.controller.retry();
    await f.controller.retry();
    expect(f.load).toHaveBeenCalledOnce();
    expect(f.next).toHaveBeenCalledOnce();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    for (const call of f.command.mock.calls) expect(call[2]).toBe(input);
    f.controller.deactivate();
  });
  it("fresh conflict requires review and late resume cannot publish after disposal", async () => {
    const f = resumeFixture();
    f.command.mockResolvedValueOnce(failed("leave_batch_policy_changed"));
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("reviewing"));
    await f.controller.save("Original");
    expect(f.controller.getSnapshot().stage).toBe("conflict");
    await f.controller.retry();
    expect(f.command).toHaveBeenCalledOnce();
    await f.controller.refresh();
    const held = deferred<Result<MutationReceipt>>();
    f.command.mockReturnValueOnce(held.promise);
    const pending = f.controller.save("Re-reviewed");
    await f.controller.save("Duplicate");
    const signal = f.command.mock.lastCall?.[3];
    f.controller.deactivate();
    held.resolve(success({ id, version: 1 }));
    await pending;
    expect(signal?.aborted).toBe(true);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "loading",
      review: null,
      batchId: null,
    });
  });
});
