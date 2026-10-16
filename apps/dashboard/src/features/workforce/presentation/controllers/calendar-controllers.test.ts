import { describe, expect, it, vi } from "vitest";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import { access, calendarFixture, employeeId, identifier } from "../../di/calendar-fixture";
import type { CalendarReview } from "../../domain/entities/calendar-review";
import type { ScheduledDay } from "../../domain/entities/scheduled-day";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { RosterDayController } from "./roster-day-controller";
import { WeeklyScheduleController } from "./weekly-schedule-controller";

const date = "2026-10-12";
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function actions() {
  return {
    loadCalendarReview: {
      execute: vi
        .fn<WorkforceUseCases["loadCalendarReview"]["execute"]>()
        .mockResolvedValue(success(calendarFixture({ from: date, until: date }))),
    },
    assignWeeklySchedule: {
      execute: vi
        .fn<WorkforceUseCases["assignWeeklySchedule"]["execute"]>()
        .mockResolvedValue(success({ id: employeeId, version: 5 })),
    },
    setRosterDay: {
      execute: vi
        .fn<WorkforceUseCases["setRosterDay"]["execute"]>()
        .mockResolvedValue(success({ id: employeeId, version: 0 })),
    },
    resetRosterDay: {
      execute: vi
        .fn<WorkforceUseCases["resetRosterDay"]["execute"]>()
        .mockResolvedValue(success({ id: employeeId, version: 8 })),
    },
  };
}
describe("calendar command ownership", () => {
  it("pins the global version and nested shift references through duplicate clicks, lost responses and MFA", async () => {
    const api = actions();
    const response = deferred<Result<MutationReceipt>>();
    api.assignWeeklySchedule.execute.mockReturnValueOnce(response.promise);
    const controller = new WeeklyScheduleController(api, access, employeeId, date, () =>
      identifier(99),
    );
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    const fields = { days: { MONDAY: { id: identifier(), version: 0 } }, reason: "Coverage" };
    const pending = controller.save(fields);
    await controller.save(fields);
    fields.days.MONDAY.version = 19;
    fields.reason = "Changed caller";
    expect(api.assignWeeklySchedule.execute).toHaveBeenCalledTimes(1);
    const original = api.assignWeeklySchedule.execute.mock.calls[0];
    expect(original?.[2]).toMatchObject({
      expectedVersion: 4,
      effectiveFrom: date,
      reason: "Coverage",
      days: { MONDAY: { version: 0 } },
    });
    expect(Object.isFrozen(original?.[2].days.MONDAY)).toBe(true);
    response.resolve(failed("connection_unavailable"));
    await pending;
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.refresh();
    expect(api.loadCalendarReview.execute).toHaveBeenCalledTimes(1);
    for (const code of ["mfa_required", "stale_version"]) {
      api.assignWeeklySchedule.execute.mockResolvedValueOnce(failed(code));
      await controller.retrySave();
      expect(controller.getSnapshot().stage).toBe("unconfirmed");
    }
    await controller.retrySave();
    expect(controller.getSnapshot().stage).toBe("saved");
    for (const call of api.assignWeeklySchedule.execute.mock.calls) {
      expect(call[1]).toBe(original?.[1]);
      expect(call[2]).toBe(original?.[2]);
    }
    controller.deactivate();
  });
  it("requires an explicit reload after a definite version conflict", async () => {
    const api = actions();
    api.assignWeeklySchedule.execute.mockResolvedValueOnce(failed("stale_version"));
    const controller = new WeeklyScheduleController(api, access, employeeId, date, () =>
      identifier(99),
    );
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    await controller.save({ days: {}, reason: "New week" });
    expect(controller.getSnapshot().stage).toBe("conflict");
    await controller.save({ days: {}, reason: "New week" });
    expect(api.assignWeeklySchedule.execute).toHaveBeenCalledTimes(1);
    api.loadCalendarReview.execute.mockResolvedValueOnce(
      success({ ...calendarFixture({ from: date, until: date }), scheduleVersion: 9 }),
    );
    await controller.refresh();
    await controller.save({ days: {}, reason: "Reviewed correction" });
    expect(api.assignWeeklySchedule.execute.mock.lastCall?.[2].expectedVersion).toBe(9);
    controller.deactivate();
  });
  it("uses the retained roster version independently of the resolved day's origin", async () => {
    const initial = calendarFixture({ from: date, until: date });
    const initialDay = initial.days[0];
    if (!initialDay) throw new Error("Fixture day missing");
    const days: ScheduledDay[] = [
      initialDay,
      {
        kind: "OFF",
        workDate: date,
        origin: "HOLIDAY",
        originVersion: 8,
        holidayId: identifier(12),
      },
      { kind: "OFF", workDate: date, origin: "ROSTER", originVersion: 7, holidayId: null },
      { kind: "UNASSIGNED", workDate: date },
    ];
    for (const [index, day] of days.entries()) {
      const api = actions();
      const active = day.kind !== "UNASSIGNED" && day.origin === "ROSTER";
      const version = active ? 7 : index === 0 ? null : 8;
      api.loadCalendarReview.execute.mockResolvedValue(
        success({
          ...initial,
          days: [day],
          rosterOverrides:
            version === null ? [] : [{ workDate: date, version, active, shift: null }],
        }),
      );
      const controller = new RosterDayController(api, access, employeeId, date, () =>
        identifier(99),
      );
      controller.activate();
      await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
      await controller.save({ shift: null, reason: "Explicit day off" });
      expect(api.setRosterDay.execute.mock.lastCall?.[2]).toEqual({
        employeeId,
        workDate: date,
        shift: null,
        reason: "Explicit day off",
        expectedVersion: version,
      });
      controller.deactivate();
    }
  });
  it("pins one reset through duplicate clicks, lost responses and later rejection", async () => {
    const api = actions();
    api.loadCalendarReview.execute.mockResolvedValue(
      success({
        ...calendarFixture({ from: date, until: date }),
        supportsRosterReset: true,
        days: [
          { kind: "OFF", workDate: date, origin: "ROSTER", originVersion: 7, holidayId: null },
        ],
        rosterOverrides: [{ workDate: date, version: 7, active: true, shift: null }],
      }),
    );
    const response = deferred<Result<MutationReceipt>>();
    api.resetRosterDay.execute.mockReturnValueOnce(response.promise);
    const controller = new RosterDayController(api, access, employeeId, date, () => identifier(99));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    const fields = { reset: true, shift: null, reason: "Return to regular schedule" };
    const pending = controller.save(fields);
    fields.reason = "Caller changed its mind";
    await controller.save(fields);
    expect(api.resetRosterDay.execute).toHaveBeenCalledTimes(1);
    expect(api.setRosterDay.execute).not.toHaveBeenCalled();
    const original = api.resetRosterDay.execute.mock.calls[0];
    expect(original?.[2]).toEqual({
      employeeId,
      workDate: date,
      expectedVersion: 7,
      reason: "Return to regular schedule",
    });
    expect(Object.isFrozen(original?.[2])).toBe(true);
    response.resolve(failed("request_timeout"));
    await pending;
    await controller.refresh();
    expect(api.loadCalendarReview.execute).toHaveBeenCalledTimes(1);
    for (const code of ["mfa_required", "roster_override_required", "stale_version"]) {
      api.resetRosterDay.execute.mockResolvedValueOnce(failed(code));
      await controller.retrySave();
      expect(controller.getSnapshot().stage).toBe("unconfirmed");
    }
    await controller.retrySave();
    expect(controller.getSnapshot().stage).toBe("saved");
    for (const call of api.resetRosterDay.execute.mock.calls) {
      expect(call[1]).toBe(original?.[1]);
      expect(call[2]).toBe(original?.[2]);
    }
    controller.deactivate();
  });
  it("only resets a reviewed active override and ignores a late receipt after disposal", async () => {
    const api = actions();
    const controller = new RosterDayController(api, access, employeeId, date, () => identifier(99));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    const fields = { reset: true, shift: null, reason: "Regular calendar" };
    await controller.save(fields);
    expect(api.resetRosterDay.execute).not.toHaveBeenCalled();
    const initial = calendarFixture({ from: date, until: date });
    api.loadCalendarReview.execute.mockResolvedValue(
      success({
        ...initial,
        rosterOverrides: [{ workDate: date, version: 7, active: false, shift: null }],
      }),
    );
    await controller.refresh();
    await controller.save(fields);
    expect(api.resetRosterDay.execute).not.toHaveBeenCalled();
    api.loadCalendarReview.execute.mockResolvedValue(
      success({
        ...initial,
        rosterOverrides: [{ workDate: date, version: 7, active: true, shift: null }],
        supportsRosterReset: false,
      }),
    );
    await controller.refresh();
    await controller.save(fields);
    expect(api.resetRosterDay.execute).not.toHaveBeenCalled();
    api.loadCalendarReview.execute.mockResolvedValue(
      success({
        ...initial,
        supportsRosterReset: true,
        days: [
          { kind: "OFF", workDate: date, origin: "ROSTER", originVersion: 7, holidayId: null },
        ],
        rosterOverrides: [{ workDate: date, version: 7, active: true, shift: null }],
      }),
    );
    await controller.refresh();
    const response = deferred<Result<MutationReceipt>>();
    api.resetRosterDay.execute.mockReturnValueOnce(response.promise);
    const pending = controller.save(fields);
    controller.deactivate();
    expect(api.resetRosterDay.execute.mock.calls[0]?.[3].aborted).toBe(true);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    response.resolve(success({ id: employeeId, version: 8 }));
    await pending;
    expect(controller.getSnapshot().receipt).toBeNull();
    expect(controller.getSnapshot().stage).toBe("editing");
    controller.deactivate();
  });
  it("does not turn an unknown roster save into an editable request after later rejection", async () => {
    const api = actions();
    api.setRosterDay.execute.mockResolvedValueOnce(failed("request_timeout"));
    const controller = new RosterDayController(api, access, employeeId, date, () => identifier(99));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    const fields = { shift: { id: identifier(), version: 0 }, reason: "Overnight roster" };
    await controller.save(fields);
    fields.shift.version = 5;
    api.setRosterDay.execute.mockResolvedValueOnce(failed("mfa_required"));
    await controller.retrySave();
    expect(controller.getSnapshot().stage).toBe("unconfirmed");
    await controller.save({ shift: null, reason: "Replace" });
    expect(api.setRosterDay.execute).toHaveBeenCalledTimes(2);
    await controller.retrySave();
    const calls = api.setRosterDay.execute.mock.calls;
    expect(calls[2]?.[2]).toBe(calls[0]?.[2]);
    expect(calls[2]?.[2].shift?.version).toBe(0);
    expect(controller.getSnapshot().stage).toBe("saved");
    controller.deactivate();
  });
  it.each(["weekly", "roster"])(
    "cancels %s reads and ignores late failures across reactivation",
    async (kind) => {
      const api = actions();
      const pending = deferred<Result<CalendarReview>>();
      api.loadCalendarReview.execute.mockReturnValueOnce(pending.promise);
      const controller =
        kind === "weekly"
          ? new WeeklyScheduleController(api, access, employeeId, date, () => identifier(99))
          : new RosterDayController(api, access, employeeId, date, () => identifier(99));
      const listener = vi.fn();
      const unsubscribe = controller.subscribe(listener);
      controller.activate();
      const signal = api.loadCalendarReview.execute.mock.calls[0]?.[3];
      controller.deactivate();
      expect(signal?.aborted).toBe(true);
      controller.activate();
      await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
      pending.resolve(failed("mfa_required"));
      await Promise.resolve();
      expect(controller.getSnapshot().failure).toBeNull();
      unsubscribe();
      const count = listener.mock.calls.length;
      controller.deactivate();
      expect(listener).toHaveBeenCalledTimes(count);
    },
  );
  it("cancels a pending save without accepting a late receipt into another owner", async () => {
    const api = actions();
    const response = deferred<Result<MutationReceipt>>();
    api.setRosterDay.execute.mockReturnValueOnce(response.promise);
    const controller = new RosterDayController(api, access, employeeId, date, () => identifier(99));
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("editing"));
    const pending = controller.save({ shift: null, reason: "Day off" });
    controller.deactivate();
    expect(api.setRosterDay.execute.mock.calls[0]?.[3].aborted).toBe(true);
    response.resolve(success({ id: employeeId, version: 0 }));
    await pending;
    expect(controller.getSnapshot().receipt).toBeNull();
    expect(controller.getSnapshot().review).toBeNull();
  });
  it("denies direct editing without management authority before loading employee data", async () => {
    const api = actions();
    const controller = new WeeklyScheduleController(
      api,
      { ...access, permissions: ["workforce.read"] },
      employeeId,
      date,
      () => identifier(99),
    );
    controller.activate();
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      failure: { code: "access_denied" },
    });
    expect(api.loadCalendarReview.execute).not.toHaveBeenCalled();
    await controller.save({ days: {}, reason: "Rejected" });
    expect(api.assignWeeklySchedule.execute).not.toHaveBeenCalled();
    controller.deactivate();
  });
});
