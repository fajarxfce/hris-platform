import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { leaveBatchAccessLost } from "../../domain/policies/leave-batch-policy";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import {
  initialLeaveAccrualScheduleState,
  type LeaveAccrualScheduleState,
} from "../models/leave-accrual-schedule-state";
/** Owns one explicit read. Workspace suspension/disposal cancels acquisition. */
export class LeaveAccrualScheduleController {
  #state = initialLeaveAccrualScheduleState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LeaveUseCases["loadAccrualSchedule"],
    private readonly access: CompanyAccess,
    private readonly id: string,
  ) {}
  getSnapshot = (): LeaveAccrualScheduleState => this.#state;
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
    this.publish(initialLeaveAccrualScheduleState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...initialLeaveAccrualScheduleState, details: this.#state.details });
    try {
      const result = await this.load.execute(this.access, this.id, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", details: result.value, failure: null }
          : {
              stage: "unavailable",
              failure: result.failure,
              details:
                leaveBatchAccessLost(result.failure) ||
                result.failure.code === "leave_accrual_schedule_not_found"
                  ? null
                  : this.#state.details,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...this.#state,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: LeaveAccrualScheduleState): void {
    this.#state = state;
    for (const listener of [...this.#listeners]) listener();
  }
}
