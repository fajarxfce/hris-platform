import { describe, expect, it, vi } from "vitest";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import {
  batchAccess as access,
  deferred,
  batchOperation as operation,
} from "../../di/leave-batch-fixture";
import { carryDetails as details, carryExpiryId as id } from "../../di/leave-carry-expiry-fixture";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveCarryExpiryResumeController } from "./leave-carry-expiry-resume-controller";

function fixture() {
  const load = vi
    .fn<LeaveUseCases["loadCarryExpiry"]["execute"]>()
    .mockResolvedValue(success(details));
  const command = vi
    .fn<LeaveUseCases["resumeCarryExpiry"]["execute"]>()
    .mockResolvedValue(success({ id, version: 1 }));
  const next = vi.fn<() => string>().mockReturnValue(operation);
  return {
    load,
    command,
    next,
    controller: new LeaveCarryExpiryResumeController(
      { execute: load },
      { execute: command },
      access,
      id,
      next,
    ),
  };
}
describe("carry expiry recovery command ownership", () => {
  it("retains one original operation through response loss, MFA and a later conflict", async () => {
    const f = fixture();
    f.command
      .mockResolvedValueOnce(failed("network_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("stale_version"));
    f.controller.activate();
    await Promise.resolve();
    await f.controller.save("  Continue remaining work  ");
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      operationId: operation,
    });
    await f.controller.refresh();
    expect(f.load).toHaveBeenCalledTimes(1);
    await f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", review: null });
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("unconfirmed");
    await f.controller.retry();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      expiryId: id,
      receipt: { id, version: 1 },
    });
    expect(f.next).toHaveBeenCalledTimes(1);
    for (const call of f.command.mock.calls)
      expect(call.slice(0, 3)).toEqual([
        access,
        operation,
        { companyId: access.companyId, id, expectedVersion: 0, reason: "Continue remaining work" },
      ]);
    f.controller.deactivate();
  });
  it("coalesces repeated submit attempts and releases a late result after disposal", async () => {
    const held = deferred<Result<MutationReceipt>>(),
      f = fixture();
    f.command.mockReturnValueOnce(held.promise);
    f.controller.activate();
    await Promise.resolve();
    const pending = f.controller.save("Continue remaining work");
    await f.controller.save("A different reason");
    await f.controller.retry();
    expect(f.command).toHaveBeenCalledTimes(1);
    const signal = f.command.mock.lastCall?.[3];
    f.controller.deactivate();
    expect(signal?.aborted).toBe(true);
    held.resolve(success({ id, version: 1 }));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "loading",
      receipt: null,
      review: null,
    });
    expect(f.next).toHaveBeenCalledTimes(1);
  });
  it("requires an explicit fresh review after a definitive initial conflict", async () => {
    const f = fixture();
    f.command.mockResolvedValueOnce(failed("stale_version"));
    f.controller.activate();
    await Promise.resolve();
    await f.controller.save("Continue remaining work");
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "conflict", operationId: null });
    await f.controller.retry();
    expect(f.command).toHaveBeenCalledTimes(1);
    await f.controller.refresh();
    expect(f.controller.getSnapshot().stage).toBe("reviewing");
    f.controller.deactivate();
  });
  it("does not acquire a hidden review or replace a command when the document is hidden", async () => {
    const f = fixture(),
      held = deferred<Result<MutationReceipt>>();
    f.controller.setVisible(false);
    f.controller.activate();
    expect(f.load).not.toHaveBeenCalled();
    f.controller.setVisible(true);
    await Promise.resolve();
    f.command.mockReturnValueOnce(held.promise);
    const pending = f.controller.save("Continue remaining work");
    const signal = f.command.mock.lastCall?.[3];
    f.controller.setVisible(false);
    expect(signal?.aborted).toBe(false);
    held.resolve(success({ id, version: 1 }));
    await pending;
    expect(f.controller.getSnapshot().stage).toBe("saved");
    f.controller.deactivate();
  });
  it("uses current action hints and cannot resume a completed or exhausted process", async () => {
    const f = fixture();
    f.load.mockResolvedValueOnce(success({ ...details, actions: [] }));
    f.controller.activate();
    await Promise.resolve();
    await f.controller.save("Continue remaining work");
    expect(f.command).not.toHaveBeenCalled();
    expect(f.next).not.toHaveBeenCalled();
    f.controller.deactivate();
  });
});
