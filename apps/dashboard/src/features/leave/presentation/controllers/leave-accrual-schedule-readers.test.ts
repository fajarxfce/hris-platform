import { afterEach, describe, expect, it, vi } from "vitest";
import { failed, success } from "../../../../core/domain/result";
import {
  accrualDetails as details,
  accrualScheduleId as id,
} from "../../di/leave-accrual-schedule-fixture";
import { batchAccess as access, deferred } from "../../di/leave-batch-fixture";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveAccrualScheduleController } from "./leave-accrual-schedule-controller";
import { LeaveAccrualSchedulesController } from "./leave-accrual-schedules-controller";

afterEach(() => vi.useRealTimers());
const listFixture = () => {
  const load = vi
    .fn<LeaveUseCases["loadAccrualSchedules"]["execute"]>()
    .mockResolvedValue(success({ items: [details.schedule], nextCursor: null }));
  return { load, controller: new LeaveAccrualSchedulesController({ execute: load }, access, null) };
};
const detailFixture = () => {
  const load = vi
    .fn<LeaveUseCases["loadAccrualSchedule"]["execute"]>()
    .mockResolvedValue(success(details));
  return { load, controller: new LeaveAccrualScheduleController({ execute: load }, access, id) };
};
describe.each([
  ["history", listFixture],
  ["detail", detailFixture],
] as const)("schedule %s ownership", (_kind, fixture) => {
  it("owns no periodic work for future months", async () => {
    vi.useFakeTimers();
    const f = fixture();
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(365 * 86400000);
    expect(f.load).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
    f.controller.deactivate();
  });
  it("cancels a pending read and rejects its late failure after reopening", async () => {
    const f = fixture(),
      held = deferred<ReturnType<typeof failed>>();
    f.load.mockReturnValueOnce(held.promise);
    f.controller.activate();
    const signal = f.load.mock.calls[0]?.[2];
    f.controller.deactivate();
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
    held.resolve(failed("access_denied"));
    await Promise.resolve();
    expect(signal?.aborted).toBe(true);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "ready", failure: null });
    f.controller.deactivate();
  });
  it("a refreshed response wins over an older exception", async () => {
    const f = fixture();
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
    const held = deferred<ReturnType<typeof failed>>();
    f.load.mockReturnValueOnce(held.promise);
    const old = f.controller.refresh();
    await f.controller.refresh();
    held.reject(Error("Obsolete private response"));
    await old;
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "ready", failure: null });
    f.controller.deactivate();
  });
  it("retains data for a transient failure and clears it for denied scope", async () => {
    const f = fixture();
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
    f.load.mockResolvedValueOnce(failed("network_unavailable"));
    await f.controller.refresh();
    expect(JSON.stringify(f.controller.getSnapshot())).toContain("Annual leave");
    f.load.mockResolvedValueOnce(failed("company_access_denied"));
    await f.controller.refresh();
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("Annual leave");
    f.controller.deactivate();
  });
  it("unsubscribed listeners receive no later notifications", async () => {
    const f = fixture(),
      listener = vi.fn(),
      remove = f.controller.subscribe(listener);
    f.controller.activate();
    await vi.waitFor(() => expect(f.controller.getSnapshot().stage).toBe("ready"));
    remove();
    const count = listener.mock.calls.length;
    await f.controller.refresh();
    f.controller.deactivate();
    expect(listener).toHaveBeenCalledTimes(count);
  });
});
