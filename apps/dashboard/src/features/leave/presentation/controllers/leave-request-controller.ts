import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { initialLeaveRequestState, type LeaveRequestState } from "../models/leave-request-state";

export class LeaveRequestController {
  #state = initialLeaveRequestState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LeaveUseCases["loadRequest"],
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly historyAfter: string | null,
  ) {}
  getSnapshot = (): LeaveRequestState => this.#state;
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
    this.publish(initialLeaveRequestState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialLeaveRequestState);
    try {
      const result = await this.load.execute(
        this.access,
        this.id,
        this.historyAfter,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", request: result.value, failure: null }
          : { stage: "unavailable", request: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          request: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: LeaveRequestState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
