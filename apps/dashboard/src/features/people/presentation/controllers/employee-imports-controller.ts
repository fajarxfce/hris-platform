import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import {
  type EmployeeImportsState,
  initialEmployeeImportsState,
} from "../models/employee-imports-state";

export class EmployeeImportsController {
  #state = initialEmployeeImportsState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: PeopleUseCases["loadEmployeeImports"],
    private readonly access: CompanyAccess,
    private readonly after: string | null,
  ) {}
  getSnapshot = (): EmployeeImportsState => this.#state;
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
    this.publish(initialEmployeeImportsState);
  };

  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialEmployeeImportsState);
    try {
      const result = await this.load.execute(this.access, this.after, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish({
        ...initialEmployeeImportsState,
        ...(result.ok
          ? ({ stage: "ready", page: result.value } as const)
          : ({ stage: "unavailable", failure: result.failure } as const)),
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialEmployeeImportsState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: EmployeeImportsState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
