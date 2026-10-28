import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseReportQuery } from "../../domain/entities/expense-report-claim";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import {
  type ExpenseReportClaimsState,
  initialExpenseReportClaimsState,
} from "../models/expense-report-claims-state";

export class ExpenseReportClaimsController {
  #state = initialExpenseReportClaimsState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: ReportingUseCases["loadExpenseClaims"],
    private readonly access: CompanyAccess,
    private readonly query: ExpenseReportQuery,
  ) {}
  getSnapshot = (): ExpenseReportClaimsState => this.#state;
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
    this.publish(initialExpenseReportClaimsState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialExpenseReportClaimsState);
    try {
      const result = await this.load.execute(this.access, this.query, pending.signal);
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
  private publish(state: ExpenseReportClaimsState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
