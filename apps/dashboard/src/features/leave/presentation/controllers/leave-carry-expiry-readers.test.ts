import { afterEach, describe, expect, it, vi } from "vitest";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toLeaveCarryExpiryDetails } from "../../data/mappers/leave-carry-expiry-mapper";
import { batchAccess as access, batchCompany, deferred } from "../../di/leave-batch-fixture";
import {
  carryDeadline,
  carryDetails as details,
  carryExpiryId as id,
  queuedCarryWire,
} from "../../di/leave-carry-expiry-fixture";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveCarryExpiriesController } from "./leave-carry-expiries-controller";
import { LeaveCarryExpiryController } from "./leave-carry-expiry-controller";

afterEach(() => vi.useRealTimers());
function fixture(queued = false) {
  const wire = queuedCarryWire();
  if (!queued) {
    wire.expiry.status = "RUNNING";
    wire.job.status = "RUNNING";
  }
  const active = toLeaveCarryExpiryDetails(wire, batchCompany, id, null);
  const load = vi
    .fn<LeaveUseCases["loadCarryExpiry"]["execute"]>()
    .mockResolvedValue(success(active));
  return {
    active,
    load,
    controller: new LeaveCarryExpiryController({ execute: load }, access, id, null),
  };
}
describe("carry expiry observation lifecycle", () => {
  it("bounds automatic progress and does not replenish its budget on visibility changes", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2027-04-01T01:00:00Z"));
    const f = fixture();
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(200_000);
    expect(f.load).toHaveBeenCalledTimes(31);
    expect(f.controller.getSnapshot().updatesPaused).toBe(true);
    expect(vi.getTimerCount()).toBe(0);
    f.controller.setVisible(false);
    f.controller.setVisible(true);
    await vi.advanceTimersByTimeAsync(200_000);
    expect(f.load).toHaveBeenCalledTimes(32);
    expect(vi.getTimerCount()).toBe(0);
    await f.controller.refresh();
    await vi.advanceTimersByTimeAsync(4000);
    expect(f.load).toHaveBeenCalledTimes(34);
    f.controller.deactivate();
    expect(vi.getTimerCount()).toBe(0);
  });
  it("does not poll a distant deadline and reacquires when a due process becomes visible", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2027-01-05T03:00:00Z"));
    const f = fixture(true);
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(6 * 60 * 60 * 1000);
    expect(f.load).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
    f.controller.setVisible(false);
    vi.setSystemTime(new Date(carryDeadline));
    f.controller.setVisible(true);
    await vi.advanceTimersByTimeAsync(4000);
    expect(f.load).toHaveBeenCalledTimes(3);
    f.controller.deactivate();
  });
  it("owns one near-deadline alarm and does not read early", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(Date.parse(carryDeadline) - 60_000));
    const f = fixture(true);
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(59_999);
    expect(f.load).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(f.load).toHaveBeenCalledTimes(2);
    f.controller.deactivate();
    expect(vi.getTimerCount()).toBe(0);
  });
  it("hidden views cancel pending work and reject a late access failure", async () => {
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
    held.resolve(failed("mfa_required"));
    await vi.advanceTimersByTimeAsync(0);
    expect(f.controller.getSnapshot().details).toBe(f.active);
    expect(f.controller.getSnapshot().failure).toBeNull();
    f.controller.setVisible(true);
    await vi.advanceTimersByTimeAsync(0);
    expect(f.load).toHaveBeenCalledTimes(3);
    f.controller.deactivate();
  });
  it("never overlaps slow reads and ignores a superseded failure", async () => {
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
  it("clears private detail and stops automatic work when current access is denied", async () => {
    vi.useFakeTimers();
    const f = fixture();
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(0);
    f.load.mockResolvedValueOnce(failed("company_access_denied"));
    await vi.advanceTimersByTimeAsync(4000);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "unavailable", details: null });
    expect(vi.getTimerCount()).toBe(0);
    f.controller.deactivate();
  });
  it("stops when the process becomes terminal", async () => {
    vi.useFakeTimers();
    const f = fixture();
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(0);
    f.load.mockResolvedValueOnce(success(details));
    await vi.advanceTimersByTimeAsync(100_000);
    expect(f.load).toHaveBeenCalledTimes(2);
    expect(vi.getTimerCount()).toBe(0);
    f.controller.deactivate();
  });
  it("keeps list reads scoped to the visible controller and disposes pending pages", async () => {
    const held = deferred<Result<{ items: (typeof details.expiry)[]; nextCursor: null }>>();
    const load = vi
      .fn<LeaveUseCases["loadCarryExpiries"]["execute"]>()
      .mockReturnValueOnce(held.promise)
      .mockResolvedValue(success({ items: [details.expiry], nextCursor: null }));
    const controller = new LeaveCarryExpiriesController({ execute: load }, access, null);
    controller.setVisible(false);
    controller.activate();
    expect(load).not.toHaveBeenCalled();
    controller.setVisible(true);
    const oldSignal = load.mock.lastCall?.[2];
    controller.setVisible(false);
    expect(oldSignal?.aborted).toBe(true);
    held.resolve(failed("mfa_required"));
    await Promise.resolve();
    expect(controller.getSnapshot().failure).toBeNull();
    controller.setVisible(true);
    await Promise.resolve();
    expect(controller.getSnapshot().page?.items).toEqual([details.expiry]);
    controller.deactivate();
    expect(controller.getSnapshot().page).toBeNull();
  });
});
