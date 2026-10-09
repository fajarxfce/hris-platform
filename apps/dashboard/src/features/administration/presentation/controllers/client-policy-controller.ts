import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import { type ClientPolicyState, initialClientPolicyState } from "../models/client-policy-state";

/** Owns one mounted company/revision review; refresh also discards stale configuration. */
export class ClientPolicyController {
  #state: ClientPolicyState = initialClientPolicyState;
  #listeners = new Set<() => void>();
  #active = false;
  #pending: AbortController | null = null;

  constructor(
    private readonly load: AdministrationUseCases["loadClientPolicy"],
    private readonly access: CompanyAccess,
    private readonly version: string | null,
  ) {}

  getSnapshot = (): ClientPolicyState => this.#state;
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
    this.publish(initialClientPolicyState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", review: null, failure: null });
    try {
      const result = await this.load.execute(this.access, this.version, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", review: result.value, failure: null }
          : { stage: "unavailable", review: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          review: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: ClientPolicyState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
