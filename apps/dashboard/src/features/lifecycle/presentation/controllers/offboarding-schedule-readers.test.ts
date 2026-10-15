import { afterEach, describe, expect, it, vi } from "vitest";
import { failed, type Result, success } from "../../../../core/domain/result";
import {
  scheduleAccess as access,
  scheduleCaseId as caseId,
  deferred,
  scheduleDetails as details,
  scheduleId as id,
  scheduleReview as review,
} from "../../di/offboarding-schedule-fixture";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { OffboardingScheduleController } from "./offboarding-schedule-controller";
import { OffboardingSchedulesController } from "./offboarding-schedules-controller";

afterEach(() => vi.useRealTimers());
function fixture(due = true) {
  const load = vi
    .fn<LifecycleUseCases["loadOffboardingSchedule"]["execute"]>()
    .mockResolvedValue(success(details));
  const now = () => Date.parse(due ? details.scheduledFor : details.createdAt);
  const controller = new OffboardingScheduleController({ execute: load }, access, caseId, id, now);
  return { controller, load };
}
describe("scheduled offboarding observation ownership", () => {
  it("does not poll future schedules, but observes a cancellation that wakes early cleanup", async () => {
    vi.useFakeTimers();
    const f = fixture(false);
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(0);
    await vi.advanceTimersByTimeAsync(600_000);
    expect(f.load).toHaveBeenCalledOnce();
    f.load.mockResolvedValue(
      success({ ...details, actions: [], job: { ...details.job, cancellationRequested: true } }),
    );
    await f.controller.refresh();
    await vi.advanceTimersByTimeAsync(4000);
    expect(f.load).toHaveBeenCalledTimes(3);
    f.controller.deactivate();
    expect(vi.getTimerCount()).toBe(0);
  });
  it("caps visible progress at thirty delayed reads and only explicit refresh replenishes the budget", async () => {
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
  });
  it("hidden views own no requests or timers and discard late responses", async () => {
    vi.useFakeTimers();
    const f = fixture();
    f.controller.setVisible(false);
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(100_000);
    expect(f.load).not.toHaveBeenCalled();
    f.controller.setVisible(true);
    await vi.advanceTimersByTimeAsync(0);
    expect(f.load).toHaveBeenCalledOnce();
    const held = deferred<Result<typeof details>>();
    f.load.mockReturnValueOnce(held.promise);
    await vi.advanceTimersByTimeAsync(4000);
    const signal = f.load.mock.lastCall?.[3];
    f.controller.setVisible(false);
    expect(signal?.aborted).toBe(true);
    expect(vi.getTimerCount()).toBe(0);
    held.resolve(success({ ...details, reason: "Obsolete response" }));
    await vi.advanceTimersByTimeAsync(0);
    expect(f.controller.getSnapshot().details?.reason).toBe(details.reason);
    f.controller.setVisible(true);
    await vi.advanceTimersByTimeAsync(4000);
    expect(f.load).toHaveBeenCalledTimes(3);
    f.controller.deactivate();
    expect(vi.getTimerCount()).toBe(0);
  });
  it("terminal jobs stop polling and access denial clears retained private details", async () => {
    vi.useFakeTimers();
    const f = fixture();
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(0);
    f.load.mockResolvedValueOnce(
      success({ ...details, job: { ...details.job, status: "FAILED" }, actions: ["recover"] }),
    );
    await vi.advanceTimersByTimeAsync(4000);
    await vi.advanceTimersByTimeAsync(20_000);
    expect(f.load).toHaveBeenCalledTimes(2);
    expect(vi.getTimerCount()).toBe(0);
    f.load.mockResolvedValueOnce(failed("offboarding_access_required"));
    await f.controller.refresh();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      details: null,
      failure: { code: "offboarding_access_required" },
    });
    f.controller.deactivate();
  });
  it("network failures stop observation without discarding immutable evidence or claiming current actions", async () => {
    vi.useFakeTimers();
    const f = fixture();
    f.controller.activate();
    await vi.advanceTimersByTimeAsync(0);
    f.load.mockRejectedValueOnce(new Error("PRIVATE"));
    await vi.advanceTimersByTimeAsync(4000);
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      details,
      failure: { code: "unexpected_error" },
    });
    await vi.advanceTimersByTimeAsync(100_000);
    expect(f.load).toHaveBeenCalledTimes(2);
    f.controller.deactivate();
  });
  it("refresh and disposal fence obsolete requests and unsubscribe listeners", async () => {
    vi.useFakeTimers();
    const f = fixture(),
      held = deferred<Result<typeof details>>();
    f.load.mockReturnValueOnce(held.promise);
    f.controller.activate();
    const signal = f.load.mock.lastCall?.[3];
    await f.controller.refresh();
    expect(signal?.aborted).toBe(true);
    held.reject(new Error("Late result"));
    await vi.advanceTimersByTimeAsync(0);
    expect(f.controller.getSnapshot()).toMatchObject({ stage: "ready", details, failure: null });
    const listener = vi.fn(),
      stop = f.controller.subscribe(listener);
    stop();
    f.controller.deactivate();
    expect(vi.getTimerCount()).toBe(0);
    expect(listener).not.toHaveBeenCalled();
  });
});
it("case and schedule list are scoped together; a denied constituent clears both", async () => {
  const loadCase = vi
    .fn<LifecycleUseCases["loadCase"]["execute"]>()
    .mockResolvedValue(success(review.case));
  const loadSchedules = vi
    .fn<LifecycleUseCases["loadOffboardingSchedules"]["execute"]>()
    .mockResolvedValue(success([details]));
  const controller = new OffboardingSchedulesController(
    { loadCase: { execute: loadCase }, loadOffboardingSchedules: { execute: loadSchedules } },
    access,
    caseId,
  );
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
  loadSchedules.mockResolvedValueOnce(failed("offboarding_access_required"));
  await controller.refresh();
  expect(controller.getSnapshot()).toMatchObject({ stage: "unavailable", items: null, case: null });
  const held = deferred<Result<readonly (typeof details)[]>>();
  loadSchedules.mockReturnValueOnce(held.promise);
  const pending = controller.refresh();
  const signal = loadSchedules.mock.lastCall?.[2];
  controller.deactivate();
  expect(signal?.aborted).toBe(true);
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
  held.resolve(failed("offboarding_access_required"));
  await pending;
  expect(controller.getSnapshot()).toMatchObject({
    stage: "ready",
    items: [details],
    case: review.case,
    failure: null,
  });
  controller.deactivate();
});
it("a thrown list constituent aborts its sibling request and suppresses its late failure", async () => {
  const held = deferred<Result<readonly (typeof details)[]>>();
  const loadCase = vi
    .fn<LifecycleUseCases["loadCase"]["execute"]>()
    .mockRejectedValue(new Error("PRIVATE"));
  const loadSchedules = vi
    .fn<LifecycleUseCases["loadOffboardingSchedules"]["execute"]>()
    .mockReturnValue(held.promise);
  const controller = new OffboardingSchedulesController(
    { loadCase: { execute: loadCase }, loadOffboardingSchedules: { execute: loadSchedules } },
    access,
    caseId,
  );
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("unavailable"));
  expect(loadSchedules.mock.lastCall?.[2].aborted).toBe(true);
  held.reject(new Error("Late private failure"));
  await held.promise.catch(() => undefined);
  expect(controller.getSnapshot()).toMatchObject({
    items: null,
    case: null,
    failure: { code: "unexpected_error" },
  });
  controller.deactivate();
});
