import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveBalanceQuery } from "../../domain/entities/leave-balance-query";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { initialLeaveBalancesState, type LeaveBalancesState } from "../models/leave-balances-state";

export class LeaveBalancesController {
  #state = initialLeaveBalancesState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LeaveUseCases["loadBalances"],
    private readonly access: CompanyAccess,
    private readonly employee: string,
    private readonly query: LeaveBalanceQuery,
  ) {}
  getSnapshot = (): LeaveBalancesState => this.#state;
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
    this.publish(initialLeaveBalancesState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialLeaveBalancesState);
    try {
      const result = await this.load.execute(
        this.access,
        this.employee,
        this.query,
        pending.signal,
      );
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
  private publish(state: LeaveBalancesState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
