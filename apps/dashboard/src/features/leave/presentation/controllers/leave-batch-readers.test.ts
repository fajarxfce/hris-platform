import { afterEach, describe, expect, it, vi } from "vitest";
import { failed, type Result, success } from "../../../../core/domain/result";
import {
  batchAccess as access,
  deferred,
  batchDetails as details,
  batchId as id,
} from "../../di/leave-batch-fixture";
import type { LeaveBatchPage } from "../../domain/entities/leave-batch";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveBatchController } from "./leave-batch-controller";
import { LeaveBatchesController } from "./leave-batches-controller";

afterEach(() => vi.useRealTimers());
function fixture(after: string | null = null) {
  const active = {
    ...details,
    actions: [],
    job: { ...details.job, status: "RUNNING" as const, failureCode: null },
  };
  const load = vi.fn<LeaveUseCases["loadBatch"]["execute"]>().mockResolvedValue(success(active));
  const controller = new LeaveBatchController({ execute: load }, access, id, after);
  return { controller, load, active };
}
describe("leave batch result observation ownership", () => {
  it("caps progress at thirty delayed reads and only manual refresh replenishes the budget", async () => {
    vi.useFakeTimers();
    const f = fixture();
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(200_000);
    expect(f.load).toHaveBeenCalledTimes(31);
    expect(f.controller.getSnapshot().updatesPaused).toBe(true);
    expect(vi.getTimerCount()).toBe(0);
    f.controller.setVisible(false);
    f.controller.setVisible(true);
    await vi.advanceTimersByTimeAsync(200_000);
    expect(f.load).toHaveBeenCalledTimes(31);
    await f.controller.refresh();
    await vi.advanceTimersByTimeAsync(4000);
    expect(f.load).toHaveBeenCalledTimes(33);
    f.controller.deactivate();
    expect(vi.getTimerCount()).toBe(0);
  });
  it("hidden views own no requests or timers and reject a late result", async () => {
    vi.useFakeTimers();
    const f = fixture();
    f.controller.setVisible(false);
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(100_000);
    expect(f.load).not.toHaveBeenCalled();
    f.controller.setVisible(true);
    await vi.advanceTimersByTimeAsync(0);
    const held = deferred<Result<typeof details>>();
    f.load.mockReturnValueOnce(held.promise);
    await vi.advanceTimersByTimeAsync(4000);
    const signal = f.load.mock.lastCall?.[3];
    f.controller.setVisible(false);
    expect(signal?.aborted).toBe(true);
    expect(vi.getTimerCount()).toBe(0);
    held.resolve(failed("access_denied"));
    await vi.advanceTimersByTimeAsync(0);
    expect(f.controller.getSnapshot().details).toBe(f.active);
    f.controller.setVisible(true);
    await vi.advanceTimersByTimeAsync(4000);
    expect(f.load).toHaveBeenCalledTimes(3);
    f.controller.deactivate();
  });
  it("cannot overlap a slow update with another timer or publish its late failure", async () => {
    vi.useFakeTimers();
    const f = fixture();
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(0);
    const held = deferred<Result<typeof details>>();
    f.load.mockReturnValueOnce(held.promise);
    await vi.advanceTimersByTimeAsync(4000);
    await vi.advanceTimersByTimeAsync(500_000);
    expect(f.load).toHaveBeenCalledTimes(2);
    expect(vi.getTimerCount()).toBe(0);
    await f.controller.refresh();
    held.reject(Error("PRIVATE"));
    await vi.advanceTimersByTimeAsync(0);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "ready",
      failure: null,
      details: f.active,
    });
    f.controller.deactivate();
  });
  it.each(["FAILED", "CANCELLED", "SUCCEEDED"] as const)(
    "terminal %s stops observation",
    async (status) => {
      vi.useFakeTimers();
      const f = fixture();
      f.controller.activate();
      await vi.advanceTimersByTimeAsync(0);
      f.load.mockResolvedValueOnce(success({ ...details, job: { ...details.job, status } }));
      await vi.advanceTimersByTimeAsync(4000);
      await vi.advanceTimersByTimeAsync(100_000);
      expect(f.load).toHaveBeenCalledTimes(2);
      expect(vi.getTimerCount()).toBe(0);
      f.controller.deactivate();
    },
  );
  it.each([
    "access_denied",
    "company_access_denied",
    "company_module_disabled",
    "leave_batch_not_found",
  ])("clears private details after %s and does not poll again", async (code) => {
    vi.useFakeTimers();
    const f = fixture();
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(0);
    f.load.mockResolvedValueOnce(failed(code));
    await vi.advanceTimersByTimeAsync(4000);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      details: null,
      failure: { code },
    });
    await vi.advanceTimersByTimeAsync(200_000);
    expect(f.load).toHaveBeenCalledTimes(2);
    f.controller.deactivate();
  });
  it("transient failure retains readable results but stops automatic work", async () => {
    vi.useFakeTimers();
    const f = fixture();
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(0);
    f.load.mockResolvedValueOnce(failed("connection_unavailable"));
    await vi.advanceTimersByTimeAsync(4000);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      details: f.active,
      failure: { code: "connection_unavailable" },
    });
    await vi.advanceTimersByTimeAsync(200_000);
    expect(f.load).toHaveBeenCalledTimes(2);
    f.controller.deactivate();
  });
  it("every observation retains its selected ordinal page", async () => {
    vi.useFakeTimers();
    const f = fixture("20");
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(8000);
    for (const call of f.load.mock.calls) expect(call.slice(0, 3)).toEqual([access, id, "20"]);
    f.controller.deactivate();
    expect(vi.getTimerCount()).toBe(0);
  });
  it("replaced scopes and unsubscribed listeners receive no late completions", async () => {
    vi.useFakeTimers();
    const f = fixture(),
      held = deferred<Result<typeof details>>();
    f.load.mockReturnValueOnce(held.promise);
    f.controller.activate();
    const signal = f.load.mock.lastCall?.[3],
      listener = vi.fn(),
      stop = f.controller.subscribe(listener);
    stop();
    f.controller.deactivate();
    held.resolve(success(details));
    await vi.advanceTimersByTimeAsync(0);
    expect(signal?.aborted).toBe(true);
    expect(listener).not.toHaveBeenCalled();
    expect(f.controller.getSnapshot().details).toBeNull();
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(0);
    expect(f.controller.getSnapshot().details).toBe(f.active);
    f.controller.deactivate();
  });
});
it("directory refresh cancels obsolete pages and keeps no private data after disposal", async () => {
  const page: LeaveBatchPage = { items: [details.batch], nextCursor: null },
    held = deferred<Result<LeaveBatchPage>>();
  const load = vi
    .fn<LeaveUseCases["loadBatches"]["execute"]>()
    .mockResolvedValue(success(page))
    .mockReturnValueOnce(held.promise);
  const controller = new LeaveBatchesController({ execute: load }, access, null);
  controller.activate();
  const signal = load.mock.lastCall?.[2];
  await controller.refresh();
  held.resolve(failed("access_denied"));
  await held.promise;
  expect(signal?.aborted).toBe(true);
  expect(controller.getSnapshot()).toMatchObject({ stage: "ready", page, failure: null });
  controller.deactivate();
  expect(controller.getSnapshot().page).toBeNull();
});
