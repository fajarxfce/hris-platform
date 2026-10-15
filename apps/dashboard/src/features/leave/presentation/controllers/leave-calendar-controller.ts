import { type Failure, failed } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveCalendarScope } from "../../domain/entities/leave-calendar";
import { isLeaveCalendarDate } from "../../domain/policies/leave-calendar-policy";
import { leavePreparationAccessLost } from "../../domain/policies/leave-preparation-policy";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { initialLeaveCalendarState, type LeaveCalendarState } from "../models/leave-calendar-state";

/** Owns one month read and one date read; selection changes never refetch the month. */
export class LeaveCalendarController {
  #state = initialLeaveCalendarState;
  #active = false;
  #monthPending: AbortController | null = null;
  #datePending: AbortController | null = null;
  #selection: Readonly<{ date: string | null; after: string | null }> = { date: null, after: null };
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LeaveUseCases["loadCalendar"],
    private readonly loadEntries: LeaveUseCases["loadCalendarEntries"],
    private readonly access: CompanyAccess,
    private readonly scope: LeaveCalendarScope | null,
    private readonly month: string | null,
  ) {}
  getSnapshot = (): LeaveCalendarState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    if (this.#active) return;
    this.#active = true;
    void this.refresh();
  };
  deactivate = (): void => {
    this.#active = false;
    this.#monthPending?.abort();
    this.#datePending?.abort();
    this.#monthPending = null;
    this.#datePending = null;
    this.publish(initialLeaveCalendarState);
  };
  selectEntries = (date: string | null, after: string | null): void => {
    if (this.#selection.date === date && this.#selection.after === after) return;
    this.#selection = { date, after };
    if (this.#active && this.#state.calendar) void this.readSelectedDate();
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#monthPending?.abort();
    this.#datePending?.abort();
    this.#datePending = null;
    const pending = new AbortController();
    this.#monthPending = pending;
    this.publish(initialLeaveCalendarState);
    try {
      const result =
        this.scope === null
          ? failed("invalid_leave_calendar_scope")
          : await this.load.execute(this.access, this.scope, this.month, pending.signal);
      if (pending.signal.aborted || this.#monthPending !== pending) return;
      if (!result.ok) {
        this.publish({
          ...initialLeaveCalendarState,
          stage: "unavailable",
          failure: result.failure,
        });
        return;
      }
      this.publish({ ...initialLeaveCalendarState, stage: "ready", calendar: result.value });
      await this.readSelectedDate();
    } catch {
      if (!pending.signal.aborted && this.#monthPending === pending)
        this.publish({
          ...initialLeaveCalendarState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#monthPending === pending) this.#monthPending = null;
    }
  };
  private async readSelectedDate(): Promise<void> {
    const calendar = this.#state.calendar;
    if (!this.#active || calendar === null || this.scope === null) return;
    this.#datePending?.abort();
    const pending = new AbortController();
    this.#datePending = pending;
    const selection = this.#selection;
    const date =
      selection.date ??
      (calendar.companyDate.startsWith(`${calendar.month}-`)
        ? calendar.companyDate
        : `${calendar.month}-01`);
    this.publish({
      ...this.#state,
      date,
      entriesStage: "loading",
      entries: null,
      after: selection.after,
      entriesFailure: null,
    });
    try {
      const result =
        !isLeaveCalendarDate(date) || !date.startsWith(`${calendar.month}-`)
          ? failed("invalid_leave_calendar_date")
          : selection.date === null && selection.after !== null
            ? failed("invalid_page")
            : await this.loadEntries.execute(
                this.access,
                this.scope,
                date,
                selection.after,
                pending.signal,
              );
      if (pending.signal.aborted || this.#datePending !== pending) return;
      if (!result.ok && leavePreparationAccessLost(result.failure)) {
        this.revoke(result.failure);
        return;
      }
      this.publish(
        result.ok
          ? { ...this.#state, entriesStage: "ready", entries: result.value, entriesFailure: null }
          : {
              ...this.#state,
              entriesStage: "unavailable",
              entries: null,
              entriesFailure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#datePending === pending)
        this.publish({
          ...this.#state,
          entriesStage: "unavailable",
          entries: null,
          entriesFailure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#datePending === pending) this.#datePending = null;
    }
  }
  private revoke(failure: Failure): void {
    this.#monthPending?.abort();
    this.#datePending?.abort();
    this.#monthPending = null;
    this.#datePending = null;
    this.publish({ ...initialLeaveCalendarState, stage: "unavailable", failure });
  }
  private publish(state: LeaveCalendarState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
