import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialPayrollCalculationsState,
  type PayrollCalculationsState,
} from "../models/payroll-calculation-state";
export class PayrollCalculationsController {
  #state = initialPayrollCalculationsState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: PayrollUseCases["loadCalculations"],
    private readonly access: CompanyAccess,
    private readonly id: string,
  ) {}
  getSnapshot = (): PayrollCalculationsState => this.#state;
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
    this.publish(initialPayrollCalculationsState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialPayrollCalculationsState);
    try {
      const result = await this.load.execute(this.access, this.id, pending.signal);
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
  private publish(state: PayrollCalculationsState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
