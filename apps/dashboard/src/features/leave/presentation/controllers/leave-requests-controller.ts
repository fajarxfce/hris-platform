import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveRequestQuery } from "../../domain/entities/leave-request";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { initialLeaveRequestsState, type LeaveRequestsState } from "../models/leave-requests-state";

export class LeaveRequestsController {
  #state = initialLeaveRequestsState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LeaveUseCases["loadRequests"],
    private readonly access: CompanyAccess,
    private readonly query: LeaveRequestQuery,
  ) {}
  getSnapshot = (): LeaveRequestsState => this.#state;
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
    this.publish(initialLeaveRequestsState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialLeaveRequestsState);
    try {
      const result = await this.load.execute(this.access, this.query, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", page: result.value, failure: null }
          : { stage: "unavailable", page: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          page: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: LeaveRequestsState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
