import type { Failure } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { employeeImportScopeLost } from "../../domain/policies/employee-import-policy";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import {
  type EmployeeImportState,
  initialEmployeeImportState,
} from "../models/employee-import-state";
export class EmployeeImportController {
  #state = initialEmployeeImportState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: PeopleUseCases["loadEmployeeImport"],
    private readonly access: CompanyAccess,
    private readonly id: string,
  ) {}
  getSnapshot = (): EmployeeImportState => this.#state;
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
    this.publish(initialEmployeeImportState);
  };

  reportScopeFailure = (failure: Failure): void => {
    if (!this.#active || !employeeImportScopeLost(failure)) return;
    this.#pending?.abort();
    this.#pending = null;
    this.publish({ ...initialEmployeeImportState, stage: "unavailable", failure });
  };

  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialEmployeeImportState);
    try {
      const result = await this.load.execute(this.access, this.id, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish({
        ...initialEmployeeImportState,
        ...(result.ok
          ? ({ stage: "ready", summary: result.value } as const)
          : ({ stage: "unavailable", failure: result.failure } as const)),
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialEmployeeImportState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: EmployeeImportState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
