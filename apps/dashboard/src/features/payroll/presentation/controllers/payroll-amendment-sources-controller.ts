import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentSourceSearch } from "../../domain/entities/payroll-amendment-preparation";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialPayrollAmendmentSourcesState,
  type PayrollAmendmentSourcesState,
} from "../models/payroll-amendment-editor-state";
export class PayrollAmendmentSourcesController {
  #state = initialPayrollAmendmentSourcesState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: PayrollUseCases["listAmendmentSources"],
    private readonly access: CompanyAccess,
    private readonly search: PayrollAmendmentSourceSearch,
  ) {}
  getSnapshot = (): PayrollAmendmentSourcesState => this.#state;
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
    this.publish(initialPayrollAmendmentSourcesState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialPayrollAmendmentSourcesState);
    try {
      const result = await this.load.execute(this.access, this.search, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", data: result.value, failure: null }
          : { stage: "unavailable", data: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          data: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: PayrollAmendmentSourcesState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
