import type { PlatformAccess } from "../../../identity/domain/entities/platform-access";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import {
  type IntegrationConfigurationState,
  initialIntegrationConfigurationState,
} from "../models/integration-configuration-state";

/** One account owns the read; hiding, refresh and replacement discard earlier observations. */
export class IntegrationConfigurationController {
  #state = initialIntegrationConfigurationState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: AdministrationUseCases["loadIntegrationConfiguration"],
    private readonly access: PlatformAccess,
  ) {}
  getSnapshot = (): IntegrationConfigurationState => this.#state;
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
    this.publish(initialIntegrationConfigurationState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", review: null, failure: null });
    try {
      const result = await this.load.execute(this.access, pending.signal);
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
  private publish(state: IntegrationConfigurationState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
