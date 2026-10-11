import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { RosterDayChange } from "../../domain/entities/roster-day-change";
import { calendarChangeWasRejected } from "../../domain/policies/calendar-change-policy";
import { canManageCalendars } from "../../domain/policies/calendar-policy";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import {
  type CalendarEditorState,
  initialCalendarEditorState,
} from "../models/calendar-editor-state";

export type RosterDayFields = Pick<RosterDayChange, "shift" | "reason">;
type Submission = Readonly<{ operation: OperationId; change: RosterDayChange }>;

/** Owns the observed per-day roster version and one immutable command until acknowledgement. */
export class RosterDayController {
  #state = initialCalendarEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<WorkforceUseCases, "loadCalendarReview" | "setRosterDay">,
    private readonly access: CompanyAccess,
    readonly employeeId: string,
    readonly date: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): CalendarEditorState => this.#state;
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
    this.#pending?.abort();
    this.#pending = null;
    this.#submission = null;
    this.publish(initialCalendarEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission !== null) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialCalendarEditorState);
    if (!canManageCalendars(this.access.permissions)) {
      this.#pending = null;
      this.publish({
        ...initialCalendarEditorState,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
      return;
    }
    try {
      const result = await this.actions.loadCalendarReview.execute(
        this.access,
        this.employeeId,
        { from: this.date, until: this.date },
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish({
        ...initialCalendarEditorState,
        ...(result.ok
          ? { stage: "editing" as const, review: result.value }
          : { stage: "unavailable" as const, failure: result.failure }),
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialCalendarEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: RosterDayFields): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing" || !this.#state.review) return;
    const day = this.#state.review.days[0];
    if (!day) return;
    const version = day.kind !== "UNASSIGNED" && day.origin === "ROSTER" ? day.originVersion : null;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      change: Object.freeze({
        ...fields,
        employeeId: this.employeeId,
        workDate: this.date,
        expectedVersion: version,
        shift: fields.shift === null ? null : Object.freeze({ ...fields.shift }),
      }),
    });
    await this.submit(false);
  };
  retrySave = async (): Promise<void> => {
    if (this.#active && this.#state.stage === "unconfirmed") await this.submit(true);
  };
  private async submit(wasUnconfirmed: boolean): Promise<void> {
    const submission = this.#submission;
    if (!submission) return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({
      ...this.#state,
      stage: "saving",
      failure: null,
      operationId: submission.operation,
    });
    try {
      let result: Result<MutationReceipt>;
      try {
        result = await this.actions.setRosterDay.execute(
          this.access,
          submission.operation,
          submission.change,
          pending.signal,
        );
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: "saved",
          failure: null,
          receipt: result.value,
          operationId: null,
        });
      } else if (!wasUnconfirmed && calendarChangeWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: ["stale_version", "employee_not_found", "shift_changed"].includes(
            result.failure.code,
          )
            ? "conflict"
            : "editing",
          failure: result.failure,
          operationId: null,
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: CalendarEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
