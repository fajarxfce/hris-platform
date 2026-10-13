import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import { type ExpensePolicyState, initialExpensePolicyState } from "../models/expense-policy-state";

export class ExpensePolicyController {
  #active = false;
  #pending: AbortController | null = null;
  #state = initialExpensePolicyState;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: ExpenseUseCases["loadPolicy"],
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly historyAfter: string | null,
  ) {}
  getSnapshot = (): ExpensePolicyState => this.#state;
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
    this.publish(initialExpensePolicyState);
  };
  selectRevision = (revision: string): void => {
    if (!this.#active || this.#state.stage !== "ready") return;
    const selected = this.#state.review?.history.items.find(
      (item) => String(item.revision) === revision,
    );
    if (selected) this.publish({ ...this.#state, selectedRevision: selected });
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialExpensePolicyState);
    try {
      const result = await this.load.execute(
        this.access,
        this.id,
        this.historyAfter,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialExpensePolicyState, stage: "ready", review: result.value }
          : { ...initialExpensePolicyState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialExpensePolicyState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: ExpensePolicyState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
