import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import {
  initialLeaveCarryExpiriesState,
  type LeaveCarryExpiriesState,
} from "../models/leave-carry-expiry-state";

export class LeaveCarryExpiriesController {
  #state = initialLeaveCarryExpiriesState;
  #active = false;
  #visible = true;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LeaveUseCases["loadCarryExpiries"],
    private readonly access: CompanyAccess,
    private readonly after: string | null,
  ) {}
  getSnapshot = (): LeaveCarryExpiriesState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  setVisible = (visible: boolean): void => {
    if (this.#visible === visible) return;
    this.#visible = visible;
    if (!visible) {
      this.#pending?.abort();
      this.#pending = null;
    } else if (this.#active) void this.refresh();
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
    this.publish(initialLeaveCarryExpiriesState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || !this.#visible) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialLeaveCarryExpiriesState);
    try {
      const result = await this.load.execute(this.access, this.after, pending.signal);
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
  private publish(state: LeaveCarryExpiriesState): void {
    this.#state = state;
    for (const listener of [...this.#listeners]) listener();
  }
}
