import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import {
  initialLifecycleTemplatesState,
  type LifecycleTemplatesState,
} from "../models/lifecycle-templates-state";

/** One applied page owns one request. Company changes discard the owner and its data. */
export class LifecycleTemplatesController {
  #state: LifecycleTemplatesState = initialLifecycleTemplatesState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LifecycleUseCases["loadTemplates"],
    private readonly access: CompanyAccess,
    private readonly after: string | null,
  ) {}

  getSnapshot = (): LifecycleTemplatesState => this.#state;
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
    this.publish(initialLifecycleTemplatesState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", page: null, failure: null });
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
  private publish(state: LifecycleTemplatesState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
