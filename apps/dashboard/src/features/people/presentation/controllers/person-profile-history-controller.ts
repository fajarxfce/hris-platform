import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import {
  initialPersonProfileHistoryState,
  type PersonProfileHistoryState,
} from "../models/person-profile-history-state";

export class PersonProfileHistoryController {
  #state: PersonProfileHistoryState = initialPersonProfileHistoryState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: PeopleUseCases["loadPersonProfileHistory"],
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly after: string | null,
  ) {}
  getSnapshot = (): PersonProfileHistoryState => this.#state;
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
    this.publish(initialPersonProfileHistoryState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", page: null, selected: null, failure: null });
    try {
      const result = await this.load.execute(this.access, this.id, this.after, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", page: result.value, selected: null, failure: null }
          : { stage: "unavailable", page: null, selected: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          page: null,
          selected: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  openRevision = (revision: string): void => {
    if (!this.#active || this.#state.stage !== "ready") return;
    const selected = this.#state.page?.items.find((item) => String(item.revision) === revision);
    if (selected) this.publish({ ...this.#state, selected });
  };
  closeRevision = (): void => {
    if (this.#state.selected) this.publish({ ...this.#state, selected: null });
  };
  private publish(state: PersonProfileHistoryState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
