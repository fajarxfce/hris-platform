import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OrganizationUseCases } from "../contracts/organization-use-cases";
import {
  initialOrganizationUnitState,
  type OrganizationUnitState,
} from "../models/organization-unit-state";

export class OrganizationUnitController {
  #state: OrganizationUnitState = initialOrganizationUnitState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: OrganizationUseCases["loadUnit"],
    private readonly access: CompanyAccess,
    private readonly id: string,
  ) {}

  getSnapshot = (): OrganizationUnitState => this.#state;
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
    this.publish(initialOrganizationUnitState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", details: null, failure: null });
    try {
      const result = await this.load.execute(this.access, this.id, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", details: result.value, failure: null }
          : { stage: "unavailable", details: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          details: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: OrganizationUnitState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
