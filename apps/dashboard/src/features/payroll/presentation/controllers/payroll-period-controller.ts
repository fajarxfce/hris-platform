import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { initialPayrollPeriodState, type PayrollPeriodState } from "../models/payroll-period-state";
export class PayrollPeriodController {
  #state = initialPayrollPeriodState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: PayrollUseCases["loadPeriod"],
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly memberAfter: string | null,
    private readonly historyAfter: string | null,
  ) {}
  getSnapshot = (): PayrollPeriodState => this.#state;
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
    this.publish(initialPayrollPeriodState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialPayrollPeriodState);
    try {
      const result = await this.load.execute(
        this.access,
        this.id,
        this.memberAfter,
        this.historyAfter,
        pending.signal,
      );
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
  private publish(state: PayrollPeriodState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
