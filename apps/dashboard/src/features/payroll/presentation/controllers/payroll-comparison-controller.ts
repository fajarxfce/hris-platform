import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialPayrollComparisonState,
  type PayrollComparisonState,
} from "../models/payroll-comparison-state";
export class PayrollComparisonController {
  #state = initialPayrollComparisonState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<PayrollUseCases, "loadComparisonContext" | "loadComparison">,
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly baseline: string | null,
    private readonly month: string | null,
    private readonly periodAfter: string | null,
    private readonly after: string | null,
    private readonly changedOnly: boolean,
  ) {}
  getSnapshot = (): PayrollComparisonState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = () => {
    if (this.#active) return;
    this.#active = true;
    void this.refresh();
  };
  deactivate = () => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.publish(initialPayrollComparisonState);
  };
  select = (id: string) => {
    if (this.#state.comparison?.employees.items.some((row) => row.employmentId === id))
      this.publish({ ...this.#state, selected: id });
  };
  dismiss = () => this.publish({ ...this.#state, selected: null });
  refresh = async () => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...initialPayrollComparisonState, stage: "loading" });
    try {
      const context = await this.actions.loadComparisonContext.execute(
        this.access,
        this.id,
        this.month,
        this.periodAfter,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (!context.ok) {
        this.publish({
          ...initialPayrollComparisonState,
          stage: "unavailable",
          failure: context.failure,
        });
        return;
      }
      if (this.baseline === null) {
        this.publish({ ...initialPayrollComparisonState, stage: "ready", context: context.value });
        return;
      }
      const compared = await this.actions.loadComparison.execute(
        this.access,
        this.id,
        this.baseline,
        this.after,
        this.changedOnly,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        compared.ok
          ? {
              stage: "ready",
              context: context.value,
              comparison: compared.value,
              selected: null,
              failure: null,
            }
          : { ...initialPayrollComparisonState, stage: "unavailable", failure: compared.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialPayrollComparisonState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: PayrollComparisonState) {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
