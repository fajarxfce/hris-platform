import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { failed, type Result, success } from "../../../../core/domain/result";
import { periodAccess, periodMonth, workPeriodOverviewFixture } from "../../di/work-period-fixture";
import type { WorkPeriodStatus } from "../../domain/entities/work-period";
import type { WorkPeriodOverview } from "../../domain/entities/work-period-overview";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { WorkPeriodController } from "./work-period-controller";

function deferred() {
  let resolve!: (value: Result<WorkPeriodOverview>) => void;
  const promise = new Promise<Result<WorkPeriodOverview>>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
const controllers: WorkPeriodController[] = [];
beforeEach(() => vi.useFakeTimers());
afterEach(() => {
  for (const controller of controllers.splice(0)) controller.deactivate();
  vi.useRealTimers();
});
function fixture(status: WorkPeriodStatus = "PROCESSING") {
  const value = workPeriodOverviewFixture(status);
  const execute = vi
    .fn<WorkforceUseCases["loadWorkPeriodOverview"]["execute"]>()
    .mockResolvedValue(success(value));
  const controller = new WorkPeriodController({ execute }, periodAccess, periodMonth);
  controllers.push(controller);
  return { controller, execute, value };
}
const settle = () => vi.advanceTimersByTimeAsync(0);

describe("work period observation", () => {
  it.each(["OPEN", "REVIEW_REQUIRED", "CLOSED"] as const)(
    "loads %s once and owns activation",
    async (status) => {
      const f = fixture(status);
      await f.controller.refresh();
      expect(f.execute).not.toHaveBeenCalled();
      f.controller.activate();
      f.controller.activate();
      await settle();
      expect(f.controller.getSnapshot().overview).toBe(f.value);
      await vi.advanceTimersByTimeAsync(600_000);
      expect(f.execute).toHaveBeenCalledOnce();
      expect(vi.getTimerCount()).toBe(0);
    },
  );

  it("never overlaps progress reads and stops after publication", async () => {
    const f = fixture(),
      held = deferred();
    f.execute
      .mockResolvedValueOnce(success(f.value))
      .mockReturnValueOnce(held.promise)
      .mockResolvedValue(success(workPeriodOverviewFixture("CLOSED")));
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(4000);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "updating", overview: f.value });
    await vi.advanceTimersByTimeAsync(60_000);
    expect(f.execute).toHaveBeenCalledTimes(2);
    expect(vi.getTimerCount()).toBe(0);
    held.resolve(success(f.value));
    await settle();
    await vi.advanceTimersByTimeAsync(4000);
    expect(f.controller.getSnapshot().overview?.period?.status).toBe("CLOSED");
    await vi.advanceTimersByTimeAsync(60_000);
    expect(f.execute).toHaveBeenCalledTimes(3);
    expect(vi.getTimerCount()).toBe(0);
  });

  it("bounds automatic observation and requires an explicit refresh to restart it", async () => {
    const f = fixture();
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(600_000);
    expect(f.execute).toHaveBeenCalledTimes(31);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "ready", updatesPaused: true });
    expect(vi.getTimerCount()).toBe(0);
    f.controller.setVisible(false);
    f.controller.setVisible(true);
    await vi.advanceTimersByTimeAsync(60_000);
    expect(f.execute).toHaveBeenCalledTimes(31);
    await f.controller.refresh();
    expect(f.controller.getSnapshot().updatesPaused).toBe(false);
    await vi.advanceTimersByTimeAsync(4000);
    expect(f.execute).toHaveBeenCalledTimes(33);
  });

  it("pauses hidden requests, rejects their late results and resumes only active progress", async () => {
    const f = fixture(),
      held = deferred();
    f.execute.mockResolvedValueOnce(success(f.value)).mockReturnValueOnce(held.promise);
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(4000);
    const signal = f.execute.mock.lastCall?.[2];
    f.controller.setVisible(false);
    expect(signal?.aborted).toBe(true);
    held.resolve(failed("mfa_required"));
    await vi.advanceTimersByTimeAsync(60_000);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "ready",
      failure: null,
      overview: f.value,
    });
    expect(f.execute).toHaveBeenCalledTimes(2);
    f.controller.setVisible(true);
    await vi.advanceTimersByTimeAsync(4000);
    expect(f.execute).toHaveBeenCalledTimes(3);
  });

  it("defers a hidden initial read until visible", async () => {
    const f = fixture("OPEN");
    f.controller.setVisible(false);
    f.controller.activate();
    await settle();
    expect(f.execute).not.toHaveBeenCalled();
    f.controller.setVisible(true);
    await settle();
    expect(f.execute).toHaveBeenCalledOnce();
    expect(f.controller.getSnapshot().stage).toBe("ready");
  });

  it("keeps denied data hidden throughout manual retries and never resumes on focus", async () => {
    const f = fixture(),
      held = deferred();
    f.execute
      .mockResolvedValueOnce(success(f.value))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockReturnValueOnce(held.promise);
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(4000);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      overview: null,
      failure: { code: "mfa_required" },
    });
    f.controller.setVisible(false);
    f.controller.setVisible(true);
    await vi.advanceTimersByTimeAsync(60_000);
    expect(f.execute).toHaveBeenCalledTimes(2);
    const retry = f.controller.refresh();
    expect(f.controller.getSnapshot().overview).toBeNull();
    held.resolve(failed("connection_unavailable"));
    await retry;
    expect(f.controller.getSnapshot().overview).toBeNull();
    expect(vi.getTimerCount()).toBe(0);
  });

  it("releases disposed requests and ignores both replaced and late results", async () => {
    const f = fixture(),
      old = deferred(),
      current = deferred();
    f.execute.mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise);
    f.controller.activate();
    const oldSignal = f.execute.mock.lastCall?.[2];
    const refresh = f.controller.refresh();
    expect(oldSignal?.aborted).toBe(true);
    old.resolve(success(f.value));
    await settle();
    expect(f.controller.getSnapshot().overview).toBeNull();
    const currentSignal = f.execute.mock.lastCall?.[2];
    f.controller.deactivate();
    expect(currentSignal?.aborted).toBe(true);
    current.resolve(failed("mfa_required"));
    await refresh;
    expect(f.controller.getSnapshot()).toMatchObject({ overview: null, failure: null });
    expect(vi.getTimerCount()).toBe(0);
  });

  it("stops on unexpected acquisition failures without exposing technical text", async () => {
    const f = fixture();
    f.execute.mockRejectedValue(new Error("PRIVATE VALUE"));
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(60_000);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      overview: null,
      failure: { code: "unexpected_error" },
    });
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
    expect(f.execute).toHaveBeenCalledOnce();
  });
});
