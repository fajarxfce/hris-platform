import { afterEach, describe, expect, it, vi } from "vitest";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { LoadEmployee } from "../../../people/domain/usecases/load-employee";
import {
  onboardingAccess as access,
  deferred,
  onboardingDetails as details,
  onboardingEmployeeDetails as employee,
  onboardingEmployee as employeeId,
  onboardingId as id,
} from "../../di/onboarding-schedule-fixture";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { OnboardingScheduleController } from "./onboarding-schedule-controller";
import { OnboardingSchedulesController } from "./onboarding-schedules-controller";

afterEach(() => vi.useRealTimers());
function fixture(due = true) {
  const load = vi
    .fn<LifecycleUseCases["loadOnboardingSchedule"]["execute"]>()
    .mockResolvedValue(success(details));
  const now = () => Date.parse(due ? details.scheduledFor : details.createdAt);
  const controller = new OnboardingScheduleController(
    { execute: load },
    access,
    employeeId,
    id,
    now,
  );
  return { controller, load };
}
describe("scheduled onboarding observation ownership", () => {
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
    f.load.mockResolvedValueOnce(failed("onboarding_access_required"));
    await f.controller.refresh();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      details: null,
      failure: { code: "onboarding_access_required" },
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

it("employee and schedule history share a cancellation scope and erase denied constituents", async () => {
  const loadEmployee = vi.fn<LoadEmployee["execute"]>().mockResolvedValue(success(employee));
  const load = vi
    .fn<LifecycleUseCases["loadOnboardingSchedules"]["execute"]>()
    .mockResolvedValue(success([details]));
  const controller = new OnboardingSchedulesController(
    { execute: loadEmployee },
    { execute: load },
    access,
    employeeId,
    "2026-10-02",
  );
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
  expect(loadEmployee.mock.lastCall?.slice(0, 3)).toEqual([access, employeeId, "2026-10-02"]);
  expect(loadEmployee.mock.lastCall?.[3]).toBe(load.mock.lastCall?.[2]);
  load.mockResolvedValueOnce(failed("onboarding_access_required"));
  await controller.refresh();
  expect(controller.getSnapshot()).toMatchObject({
    stage: "unavailable",
    employee: null,
    items: null,
  });
  const held = deferred<Result<readonly (typeof details)[]>>();
  load.mockReturnValueOnce(held.promise);
  const pending = controller.refresh(),
    signal = load.mock.lastCall?.[2];
  controller.deactivate();
  expect(signal?.aborted).toBe(true);
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
  held.resolve(failed("onboarding_access_required"));
  await pending;
  expect(controller.getSnapshot()).toMatchObject({
    stage: "ready",
    employee,
    items: [details],
    failure: null,
  });
  controller.deactivate();
});
it("a thrown constituent aborts its sibling and ignores late failure", async () => {
  const held = deferred<Result<readonly (typeof details)[]>>();
  const loadEmployee = vi.fn<LoadEmployee["execute"]>().mockRejectedValue(new Error("PRIVATE"));
  const load = vi
    .fn<LifecycleUseCases["loadOnboardingSchedules"]["execute"]>()
    .mockReturnValue(held.promise);
  const controller = new OnboardingSchedulesController(
    { execute: loadEmployee },
    { execute: load },
    access,
    employeeId,
    "2026-10-02",
  );
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("unavailable"));
  expect(load.mock.lastCall?.[2].aborted).toBe(true);
  held.reject(new Error("PRIVATE late error"));
  await held.promise.catch(() => undefined);
  expect(controller.getSnapshot()).toMatchObject({
    employee: null,
    items: null,
    failure: { code: "unexpected_error" },
  });
  controller.deactivate();
});
it("discovery without management does not acquire either private projection", async () => {
  const loadEmployee = vi.fn<LoadEmployee["execute"]>(),
    load = vi.fn<LifecycleUseCases["loadOnboardingSchedules"]["execute"]>();
  const controller = new OnboardingSchedulesController(
    { execute: loadEmployee },
    { execute: load },
    { ...access, permissions: ["people.read", "people.lifecycle.read"] },
    employeeId,
    "2026-10-02",
  );
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("unavailable"));
  expect(load).not.toHaveBeenCalled();
  expect(loadEmployee).not.toHaveBeenCalled();
  controller.deactivate();
});
