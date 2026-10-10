import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleCaseSearch } from "../../domain/entities/lifecycle-case-search";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import {
  initialLifecycleCasesState,
  type LifecycleCasesState,
} from "../models/lifecycle-cases-state";

export class LifecycleCasesController {
  #state = initialLifecycleCasesState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LifecycleUseCases["loadCases"],
    private readonly access: CompanyAccess,
    private readonly search: LifecycleCaseSearch,
  ) {}
  getSnapshot = (): LifecycleCasesState => this.#state;
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
    this.publish(initialLifecycleCasesState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialLifecycleCasesState);
    try {
      const result = await this.load.execute(this.access, this.search, pending.signal);
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
  private publish(state: LifecycleCasesState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
