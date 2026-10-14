import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { TaxOpeningSearch } from "../../domain/entities/tax-opening-review";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialTaxOpeningEmployeesState,
  type TaxOpeningEmployeesState,
} from "../models/tax-opening-employees-state";
export class TaxOpeningEmployeesController {
  #state = initialTaxOpeningEmployeesState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: PayrollUseCases["listTaxOpeningEmployees"],
    private readonly access: CompanyAccess,
    private readonly search: TaxOpeningSearch,
  ) {}
  getSnapshot = (): TaxOpeningEmployeesState => this.#state;
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
    this.publish(initialTaxOpeningEmployeesState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialTaxOpeningEmployeesState);
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
  private publish(state: TaxOpeningEmployeesState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
