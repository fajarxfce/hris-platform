import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import {
  type EmploymentTransfersState,
  initialEmploymentTransfersState,
} from "../models/employment-transfers-state";

export class EmploymentTransfersController {
  #state = initialEmploymentTransfersState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: PeopleUseCases["loadEmploymentTransfers"],
    private readonly access: CompanyAccess,
    private readonly id: string,
  ) {}
  getSnapshot = (): EmploymentTransfersState => this.#state;
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
    this.publish(initialEmploymentTransfersState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialEmploymentTransfersState);
    try {
      const result = await this.load.execute(this.access, this.id, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", items: result.value, failure: null }
          : { stage: "unavailable", items: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          items: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: EmploymentTransfersState): void {
    this.#state = Object.freeze(state);
    for (const listener of this.#listeners) listener();
  }
}
