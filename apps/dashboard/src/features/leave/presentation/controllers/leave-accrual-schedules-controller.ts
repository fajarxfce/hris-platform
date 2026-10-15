import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { leaveBatchAccessLost } from "../../domain/policies/leave-batch-policy";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import {
  initialLeaveAccrualSchedulesState,
  type LeaveAccrualSchedulesState,
} from "../models/leave-accrual-schedule-state";
/** Owns one explicit read. Workspace suspension/disposal cancels acquisition. */
export class LeaveAccrualSchedulesController {
  #state = initialLeaveAccrualSchedulesState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LeaveUseCases["loadAccrualSchedules"],
    private readonly access: CompanyAccess,
    private readonly after: string | null,
  ) {}
  getSnapshot = (): LeaveAccrualSchedulesState => this.#state;
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
    this.publish(initialLeaveAccrualSchedulesState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...initialLeaveAccrualSchedulesState, page: this.#state.page });
    try {
      const result = await this.load.execute(this.access, this.after, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", page: result.value, failure: null }
          : {
              stage: "unavailable",
              failure: result.failure,
              page:
                leaveBatchAccessLost(result.failure) ||
                result.failure.code === "leave_accrual_schedule_not_found"
                  ? null
                  : this.#state.page,
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
  private publish(state: LeaveAccrualSchedulesState): void {
    this.#state = state;
    for (const listener of [...this.#listeners]) listener();
  }
}
