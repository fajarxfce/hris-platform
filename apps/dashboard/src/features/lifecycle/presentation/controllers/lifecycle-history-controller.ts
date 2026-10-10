import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import {
  initialLifecycleHistoryState,
  type LifecycleHistoryState,
} from "../models/lifecycle-history-state";

export class LifecycleHistoryController {
  #state = initialLifecycleHistoryState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LifecycleUseCases["loadHistory"],
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly after: string | null,
  ) {}
  getSnapshot = (): LifecycleHistoryState => this.#state;
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
    this.publish(initialLifecycleHistoryState);
  };
  open = (version: string): void => {
    const selected = this.#state.page?.items.find((item) => String(item.version) === version);
    if (this.#active && selected) this.publish({ ...this.#state, selected });
  };
  close = (): void => {
    if (this.#active) this.publish({ ...this.#state, selected: null });
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialLifecycleHistoryState);
    try {
      const result = await this.load.execute(this.access, this.id, this.after, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish({
        ...initialLifecycleHistoryState,
        ...(result.ok
          ? ({ stage: "ready", page: result.value } as const)
          : ({ stage: "unavailable", failure: result.failure } as const)),
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialLifecycleHistoryState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: LifecycleHistoryState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
