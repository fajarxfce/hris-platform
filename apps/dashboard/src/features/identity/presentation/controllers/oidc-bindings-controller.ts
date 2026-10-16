import type { PlatformAccess } from "../../domain/entities/platform-access";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import { initialOidcBindingsState, type OidcBindingsState } from "../models/oidc-bindings-state";

export class OidcBindingsController {
  #state = initialOidcBindingsState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: IdentityAdministrationUseCases["loadOidcBindings"],
    private readonly access: PlatformAccess,
    private readonly accountId: string,
    private readonly after: string | null,
  ) {}
  getSnapshot = (): OidcBindingsState => this.#state;
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
    this.publish(initialOidcBindingsState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialOidcBindingsState);
    try {
      const result = await this.load.execute(
        this.access,
        this.accountId,
        this.after,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", directory: result.value, failure: null }
          : { stage: "unavailable", directory: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          directory: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: OidcBindingsState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
