import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialPayrollRunEmployeeState,
  type PayrollRunEmployeeState,
} from "../models/payroll-calculation-state";
export class PayrollRunEmployeeController {
  #state = initialPayrollRunEmployeeState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: PayrollUseCases["loadRunEmployee"],
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly employeeId: string,
  ) {}
  getSnapshot = (): PayrollRunEmployeeState => this.#state;
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
    this.publish(initialPayrollRunEmployeeState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialPayrollRunEmployeeState);
    try {
      const result = await this.load.execute(this.access, this.id, this.employeeId, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", employee: result.value, failure: null }
          : { stage: "unavailable", employee: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          employee: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: PayrollRunEmployeeState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
