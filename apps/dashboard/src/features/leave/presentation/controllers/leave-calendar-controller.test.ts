import { afterEach, describe, expect, it, vi } from "vitest";
import { failed, type Result, success } from "../../../../core/domain/result";
import { toLeaveCalendar, toLeaveCalendarEntries } from "../../data/mappers/leave-calendar-mapper";
import {
  calendarAccess,
  calendarCompany,
  calendarDeferred,
  calendarEntriesWire,
  calendarRequestId,
  calendarWire,
} from "../../di/leave-calendar-fixture";
import type { LeaveCalendar } from "../../domain/entities/leave-calendar";
import type { LeaveCalendarEntries } from "../../domain/entities/leave-calendar-entry";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { LeaveCalendarController } from "./leave-calendar-controller";

const month = toLeaveCalendar(calendarWire, calendarCompany, "COMPANY", null);
const entries = toLeaveCalendarEntries(
  calendarEntriesWire,
  calendarCompany,
  "COMPANY",
  calendarEntriesWire.date,
  null,
);
const owners: LeaveCalendarController[] = [];
function setup() {
  const load = {
    execute: vi.fn<LeaveUseCases["loadCalendar"]["execute"]>().mockResolvedValue(success(month)),
  };
  const detail = {
    execute: vi
      .fn<LeaveUseCases["loadCalendarEntries"]["execute"]>()
      .mockImplementation(async (_access, scope, date) => success({ ...entries, scope, date })),
  };
  const owner = new LeaveCalendarController(load, detail, calendarAccess, "COMPANY", null);
  owners.push(owner);
  return { owner, load, detail };
}
afterEach(() => {
  for (const owner of owners.splice(0)) owner.deactivate();
  vi.useRealTimers();
});
describe("calendar request ownership", () => {
  it("activates once and derives the initial date from the server", async () => {
    const { owner, load, detail } = setup();
    owner.activate();
    owner.activate();
    await vi.waitFor(() => expect(owner.getSnapshot().entriesStage).toBe("ready"));
    expect(load.execute).toHaveBeenCalledTimes(1);
    expect(detail.execute).toHaveBeenCalledTimes(1);
    expect(owner.getSnapshot().date).toBe("2026-10-15");
  });
  it("date selection and pagination do not reload the month", async () => {
    const { owner, load, detail } = setup();
    owner.activate();
    await vi.waitFor(() => expect(owner.getSnapshot().entriesStage).toBe("ready"));
    owner.selectEntries("2026-10-05", null);
    await vi.waitFor(() => expect(owner.getSnapshot().entries?.date).toBe("2026-10-05"));
    owner.selectEntries("2026-10-05", calendarRequestId(1));
    await vi.waitFor(() => expect(detail.execute).toHaveBeenCalledTimes(3));
    expect(load.execute).toHaveBeenCalledTimes(1);
    expect(detail.execute.mock.calls[2]?.slice(1, 4)).toEqual([
      "COMPANY",
      "2026-10-05",
      calendarRequestId(1),
    ]);
  });
  it("selects only the latest date while the month is still pending", async () => {
    const { owner, load, detail } = setup(),
      pending = calendarDeferred<Result<LeaveCalendar>>();
    load.execute.mockReturnValue(pending.promise);
    owner.activate();
    owner.selectEntries("2026-10-05", null);
    owner.selectEntries("2026-10-06", null);
    expect(detail.execute).not.toHaveBeenCalled();
    pending.resolve(success(month));
    await vi.waitFor(() => expect(owner.getSnapshot().entriesStage).toBe("ready"));
    expect(detail.execute).toHaveBeenCalledTimes(1);
    expect(detail.execute.mock.calls[0]?.[2]).toBe("2026-10-06");
  });
  it.each(["success", "failure", "exception"])(
    "rejects a late date %s after another date is selected",
    async (outcome) => {
      const { owner, detail } = setup(),
        pending = calendarDeferred<Result<LeaveCalendarEntries>>();
      detail.execute.mockReturnValueOnce(pending.promise);
      owner.activate();
      await vi.waitFor(() => expect(detail.execute).toHaveBeenCalledTimes(1));
      const oldSignal = detail.execute.mock.calls[0]?.[4];
      owner.selectEntries("2026-10-06", null);
      await vi.waitFor(() => expect(owner.getSnapshot().entries?.date).toBe("2026-10-06"));
      expect(oldSignal?.aborted).toBe(true);
      const current = owner.getSnapshot();
      if (outcome === "exception") pending.reject(Error("late private failure"));
      else pending.resolve(outcome === "success" ? success(entries) : failed("access_denied"));
      await Promise.resolve();
      await Promise.resolve();
      expect(owner.getSnapshot()).toBe(current);
    },
  );
  it("refresh clears both projections and rejects an older month result", async () => {
    const { owner, load, detail } = setup(),
      old = calendarDeferred<Result<LeaveCalendar>>();
    load.execute.mockReturnValueOnce(old.promise);
    owner.activate();
    const oldSignal = load.execute.mock.calls[0]?.[3];
    await owner.refresh();
    expect(oldSignal?.aborted).toBe(true);
    old.resolve(success({ ...month, month: "2026-09" }));
    await Promise.resolve();
    await Promise.resolve();
    expect(owner.getSnapshot().calendar?.month).toBe("2026-10");
    expect(detail.execute).toHaveBeenCalledTimes(1);
  });
  it.each(["access_denied", "mfa_required", "company_module_disabled", "session_revoked"])(
    "clears the whole calendar after date failure %s",
    async (code) => {
      const { owner, detail } = setup();
      detail.execute.mockResolvedValueOnce(failed(code));
      owner.activate();
      await vi.waitFor(() => expect(owner.getSnapshot().stage).toBe("unavailable"));
      expect(owner.getSnapshot()).toMatchObject({
        calendar: null,
        entries: null,
        failure: { code },
      });
      await owner.refresh();
      expect(owner.getSnapshot().entriesStage).toBe("ready");
    },
  );
  it("a transient day failure leaves the authorized month available", async () => {
    const { owner, detail } = setup();
    detail.execute.mockResolvedValueOnce(failed("network_unavailable"));
    owner.activate();
    await vi.waitFor(() => expect(owner.getSnapshot().entriesStage).toBe("unavailable"));
    expect(owner.getSnapshot().calendar).toBe(month);
    expect(owner.getSnapshot().failure).toBeNull();
    owner.selectEntries("2026-10-06", null);
    await vi.waitFor(() => expect(owner.getSnapshot().entriesStage).toBe("ready"));
  });
  it.each(["2026-11-01", "2026-02-30", "invalid"])(
    "does not read a date outside the displayed month: %s",
    async (date) => {
      const { owner, detail } = setup();
      owner.selectEntries(date, null);
      owner.activate();
      await vi.waitFor(() => expect(owner.getSnapshot().entriesStage).toBe("unavailable"));
      expect(detail.execute).not.toHaveBeenCalled();
    },
  );
  it("an unbound default-date cursor is never sent to the API", async () => {
    const { owner, detail } = setup();
    owner.selectEntries(null, calendarRequestId(1));
    owner.activate();
    await vi.waitFor(() => expect(owner.getSnapshot().entriesFailure?.code).toBe("invalid_page"));
    expect(detail.execute).not.toHaveBeenCalled();
  });
  it("disposal cancels work and releases private snapshots and observers", async () => {
    const { owner, detail } = setup(),
      pending = calendarDeferred<Result<LeaveCalendarEntries>>(),
      observer = vi.fn();
    detail.execute.mockReturnValueOnce(pending.promise);
    const unsubscribe = owner.subscribe(observer);
    owner.activate();
    await vi.waitFor(() => expect(detail.execute).toHaveBeenCalledTimes(1));
    const signal = detail.execute.mock.calls[0]?.[4];
    owner.deactivate();
    unsubscribe();
    const calls = observer.mock.calls.length;
    pending.reject(Error("late"));
    await Promise.resolve();
    await Promise.resolve();
    expect(signal?.aborted).toBe(true);
    expect(owner.getSnapshot().calendar).toBeNull();
    expect(owner.getSnapshot().entries).toBeNull();
    expect(observer).toHaveBeenCalledTimes(calls);
    await owner.refresh();
    expect(detail.execute).toHaveBeenCalledTimes(1);
  });
});
