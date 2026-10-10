import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeSearch } from "../../domain/entities/employee-search";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import {
  type EmployeeDirectoryState,
  initialEmployeeDirectoryState,
} from "../models/employee-directory-state";

/** One URL page owns one request; no employee cache survives a scope change. */
export class EmployeeDirectoryController {
  #state: EmployeeDirectoryState = initialEmployeeDirectoryState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  private readonly search: EmployeeSearch;
  constructor(
    private readonly load: PeopleUseCases["loadEmployees"],
    private readonly access: CompanyAccess,
    search: EmployeeSearch,
  ) {
    this.search = Object.freeze({ ...search });
  }
  getSnapshot = (): EmployeeDirectoryState => this.#state;
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
    this.publish(initialEmployeeDirectoryState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", page: null, failure: null });
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
  private publish(state: EmployeeDirectoryState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
