import type { PlatformAccess } from "../../domain/entities/platform-access";
import type { ManagedAccountsQuery } from "../contracts/managed-accounts-query";
import {
  initialManagedAccountsState,
  type ManagedAccountsState,
} from "../models/managed-accounts-state";
export class ManagedAccountsController<Access = PlatformAccess> {
  #state = initialManagedAccountsState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: ManagedAccountsQuery<Access>,
    private readonly access: Access,
    private readonly query: string,
    private readonly after: string | null,
  ) {}
  getSnapshot = (): ManagedAccountsState => this.#state;
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
    this.publish(initialManagedAccountsState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialManagedAccountsState);
    try {
      const result = await this.load.execute(this.access, this.query, this.after, pending.signal);
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
  private publish(state: ManagedAccountsState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
