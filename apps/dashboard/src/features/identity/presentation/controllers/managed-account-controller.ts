import type { PlatformAccess } from "../../domain/entities/platform-access";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import {
  initialManagedAccountState,
  type ManagedAccountState,
} from "../models/managed-account-state";
export class ManagedAccountController {
  #state = initialManagedAccountState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: IdentityAdministrationUseCases["loadManagedAccount"],
    private readonly access: PlatformAccess,
    private readonly id: string,
  ) {}
  getSnapshot = (): ManagedAccountState => this.#state;
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
    this.publish(initialManagedAccountState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialManagedAccountState);
    try {
      const result = await this.load.execute(this.access, this.id, pending.signal);
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
  private publish(state: ManagedAccountState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
