import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveBalanceQuery } from "../../domain/entities/leave-balance-query";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { initialLeaveLedgerState, type LeaveLedgerState } from "../models/leave-ledger-state";

export class LeaveLedgerController {
  #state = initialLeaveLedgerState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LeaveUseCases["loadLedger"],
    private readonly access: CompanyAccess,
    private readonly employee: string,
    private readonly type: string,
    private readonly query: LeaveBalanceQuery,
  ) {}
  getSnapshot = (): LeaveLedgerState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  select = (id: string): void => {
    if (!this.#active || this.#state.stage !== "ready") return;
    const selected = this.#state.ledger?.entries.find((entry) => entry.id === id);
    if (selected) this.publish({ ...this.#state, selected });
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
    this.publish(initialLeaveLedgerState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialLeaveLedgerState);
    try {
      const result = await this.load.execute(
        this.access,
        this.employee,
        this.type,
        this.query,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", ledger: result.value, selected: null, failure: null }
          : { stage: "unavailable", ledger: null, selected: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          ledger: null,
          selected: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: LeaveLedgerState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
