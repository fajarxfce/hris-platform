import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import {
  type EmployeeImportRowsState,
  initialEmployeeImportRowsState,
} from "../models/employee-import-rows-state";

export class EmployeeImportRowsController {
  #state = initialEmployeeImportRowsState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: PeopleUseCases["loadEmployeeImportRows"],
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly after: string | null,
  ) {}
  getSnapshot = (): EmployeeImportRowsState => this.#state;
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
    this.publish(initialEmployeeImportRowsState);
  };

  open = (number: string): void => {
    const selected = this.#state.page?.items.find((item) => String(item.number) === number);
    if (this.#active && selected) this.publish({ ...this.#state, selected });
  };
  close = (): void => {
    if (this.#active) this.publish({ ...this.#state, selected: null });
  };

  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialEmployeeImportRowsState);
    try {
      const result = await this.load.execute(this.access, this.id, this.after, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish({
        ...initialEmployeeImportRowsState,
        ...(result.ok
          ? ({ stage: "ready", page: result.value } as const)
          : ({ stage: "unavailable", failure: result.failure } as const)),
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialEmployeeImportRowsState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: EmployeeImportRowsState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
