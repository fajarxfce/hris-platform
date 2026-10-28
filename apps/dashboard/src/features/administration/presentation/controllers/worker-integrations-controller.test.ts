import { describe, expect, it, vi } from "vitest";
import type { AccountId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { WorkerIntegrations } from "../../domain/entities/worker-integrations";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import { WorkerIntegrationsController } from "./worker-integrations-controller";

const access = {
  accountId: "20000000-0000-4000-8000-000000000001" as AccountId,
  permissions: ["identity.manage"],
};
const review: WorkerIntegrations = { observedAt: "2026-10-28T16:00:00Z", workers: [] };
function pending() {
  let resolve!: (value: Result<WorkerIntegrations>) => void;
  let reject!: (value: unknown) => void;
  const promise = new Promise<Result<WorkerIntegrations>>((ok, fail) => {
    resolve = ok;
    reject = fail;
  });
  return { promise, resolve, reject };
}
const flush = async () => {
  await Promise.resolve();
  await Promise.resolve();
};

describe("worker report observation ownership", () => {
  it("starts one read and aborts/discards late success on hiding", async () => {
    const first = pending(),
      execute = vi
        .fn<AdministrationUseCases["loadWorkerIntegrations"]["execute"]>()
        .mockReturnValue(first.promise);
    const controller = new WorkerIntegrationsController({ execute }, access);
    controller.activate();
    controller.activate();
    expect(execute).toHaveBeenCalledTimes(1);
    const signal = execute.mock.calls[0]?.[1];
    if (!signal) throw new Error("Expected an owned request signal");
    controller.deactivate();
    expect(signal.aborted).toBe(true);
    first.resolve(success(review));
    await flush();
    expect(controller.getSnapshot()).toEqual({ stage: "idle", review: null, failure: null });
    await controller.refresh();
    expect(execute).toHaveBeenCalledTimes(1);
  });
  it("clears old reports during refresh and keeps failed acquisition distinct from absence", async () => {
    const next = pending(),
      execute = vi
        .fn<AdministrationUseCases["loadWorkerIntegrations"]["execute"]>()
        .mockResolvedValueOnce(success(review))
        .mockReturnValueOnce(next.promise)
        .mockResolvedValueOnce(success(review));
    const controller = new WorkerIntegrationsController({ execute }, access);
    controller.activate();
    await flush();
    expect(controller.getSnapshot().review).toEqual(review);
    const refreshed = controller.refresh();
    expect(controller.getSnapshot()).toEqual({ stage: "loading", review: null, failure: null });
    next.resolve(failed("connection_unavailable"));
    await refreshed;
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      failure: { code: "connection_unavailable" },
    });
    await controller.refresh();
    expect(controller.getSnapshot()).toEqual({ stage: "ready", review, failure: null });
    controller.deactivate();
  });
  it("ignores superseded failure after a new observation succeeds", async () => {
    const old = pending(),
      execute = vi
        .fn<AdministrationUseCases["loadWorkerIntegrations"]["execute"]>()
        .mockReturnValueOnce(old.promise)
        .mockResolvedValueOnce(success(review));
    const controller = new WorkerIntegrationsController({ execute }, access);
    controller.activate();
    await controller.refresh();
    expect(execute.mock.calls[0]?.[1].aborted).toBe(true);
    old.reject(new Error("PRIVATE PROVIDER RESPONSE"));
    await flush();
    expect(controller.getSnapshot()).toEqual({ stage: "ready", review, failure: null });
    controller.deactivate();
  });
  it("reacquires on restored visibility and normalizes unexpected failures without raw details", async () => {
    const execute = vi
      .fn<AdministrationUseCases["loadWorkerIntegrations"]["execute"]>()
      .mockRejectedValueOnce(new Error("PRIVATE PROVIDER RESPONSE"))
      .mockResolvedValueOnce(success(review));
    const controller = new WorkerIntegrationsController({ execute }, access);
    controller.activate();
    await flush();
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      review: null,
      failure: { code: "unexpected_error" },
    });
    expect(JSON.stringify(controller.getSnapshot())).not.toContain("PRIVATE");
    controller.deactivate();
    controller.activate();
    await flush();
    expect(execute).toHaveBeenCalledTimes(2);
    expect(controller.getSnapshot().review).toEqual(review);
    controller.deactivate();
  });
});
