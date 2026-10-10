import { expect, it, vi } from "vitest";
import { leaveRecord } from "../../../../../tests/fixtures/leave";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toLeaveRequestDetails } from "../../data/mappers/leave-request-details-mapper";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveAttachmentsController } from "./leave-attachments-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: [] };
const detail = toLeaveRequestDetails(leaveRecord(), companyId, null);
function deferred() {
  let resolve!: (value: Result<void>) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<Result<void>>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
function fixture() {
  const execute = vi
    .fn<LeaveUseCases["downloadAttachment"]["execute"]>()
    .mockResolvedValue(success(undefined));
  const controller = new LeaveAttachmentsController({ execute }, access, detail);
  return { execute, controller };
}
it("starts only on explicit selection, serializes downloads and retains no bytes in state", async () => {
  const f = fixture();
  await f.controller.open("a");
  f.controller.activate();
  f.controller.activate();
  expect(f.execute).not.toHaveBeenCalled();
  const held = deferred();
  f.execute.mockReturnValueOnce(held.promise);
  const work = f.controller.open("a");
  await f.controller.open("b");
  expect(f.execute).toHaveBeenCalledOnce();
  expect(f.execute.mock.lastCall?.slice(0, 3)).toEqual([access, detail, "a"]);
  expect(f.controller.getSnapshot()).toEqual({
    stage: "downloading",
    revisionId: "a",
    failure: null,
  });
  held.resolve(success(undefined));
  await work;
  expect(f.controller.getSnapshot()).toEqual({ stage: "started", revisionId: "a", failure: null });
  f.controller.deactivate();
});
it.each(["result", "failure"] as const)(
  "cancellation and reactivation reject a late %s",
  async (kind) => {
    const f = fixture();
    f.controller.activate();
    const held = deferred();
    f.execute.mockReturnValueOnce(held.promise);
    const work = f.controller.open("old");
    const signal = f.execute.mock.lastCall?.[3];
    f.controller.cancel();
    expect(signal?.aborted).toBe(true);
    await f.controller.open("new");
    if (kind === "result") held.resolve(success(undefined));
    else held.reject(new Error("PRIVATE SDK FAILURE"));
    await work;
    expect(f.controller.getSnapshot()).toEqual({
      stage: "started",
      revisionId: "new",
      failure: null,
    });
    const listener = vi.fn();
    const detach = f.controller.subscribe(listener);
    detach();
    f.controller.deactivate();
    expect(listener).not.toHaveBeenCalled();
    expect(f.controller.getSnapshot()).toEqual({ stage: "idle", revisionId: null, failure: null });
    f.controller.activate();
    await f.controller.open("restored");
    expect(f.controller.getSnapshot().revisionId).toBe("restored");
    f.controller.deactivate();
  },
);
it("disposal aborts an owned download and suppresses late failure", async () => {
  const f = fixture();
  f.controller.activate();
  const held = deferred();
  f.execute.mockReturnValueOnce(held.promise);
  const work = f.controller.open("a");
  const signal = f.execute.mock.lastCall?.[3];
  f.controller.deactivate();
  held.reject(new Error("PRIVATE SDK FAILURE"));
  await work;
  expect(signal?.aborted).toBe(true);
  expect(f.controller.getSnapshot().failure).toBeNull();
  await f.controller.open("b");
  expect(f.execute).toHaveBeenCalledOnce();
});
it("permits explicit retries for transient failures but waits for fresh access after revocation", async () => {
  const f = fixture();
  f.controller.activate();
  f.execute.mockResolvedValueOnce(failed("mfa_required"));
  await f.controller.open("a");
  expect(f.controller.getSnapshot().stage).toBe("failed");
  await f.controller.open("a");
  expect(f.controller.getSnapshot().stage).toBe("started");
  f.execute.mockResolvedValueOnce(failed("leave_attachment_not_found"));
  await f.controller.open("a");
  expect(f.controller.getSnapshot().stage).toBe("unavailable");
  await f.controller.open("a");
  expect(f.execute).toHaveBeenCalledTimes(3);
  f.controller.deactivate();
});
