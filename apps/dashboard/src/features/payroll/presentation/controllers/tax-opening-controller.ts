import type { Failure } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { taxOpeningAccessLost } from "../../domain/policies/tax-opening-access-policy";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { initialTaxOpeningState, type TaxOpeningState } from "../models/tax-opening-state";
export class TaxOpeningController {
  #state = initialTaxOpeningState;
  #active = false;
  #pending: AbortController | null = null;
  #inspection: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      PayrollUseCases,
      "loadTaxOpeningReview" | "loadTaxOpeningRevision"
    >,
    private readonly access: CompanyAccess,
    private readonly employee: string,
    private readonly year: string,
    private readonly after: string | null,
  ) {}
  getSnapshot = (): TaxOpeningState => this.#state;
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
    this.#inspection?.abort();
    this.#inspection = null;
    this.publish(initialTaxOpeningState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    this.#inspection?.abort();
    this.#inspection = null;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialTaxOpeningState);
    try {
      const result = await this.actions.loadTaxOpeningReview.execute(
        this.access,
        this.employee,
        this.year,
        this.after,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialTaxOpeningState, stage: "ready", review: result.value }
          : { ...initialTaxOpeningState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.fail({ code: "unexpected_error", fields: {}, parameters: {} }, true);
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  selectRevision = async (id: string): Promise<void> => {
    if (!this.#active || this.#state.stage !== "ready") return;
    const summary = this.#state.review?.history.items.find((row) => String(row.version) === id);
    if (!summary) return;
    this.#inspection?.abort();
    const pending = new AbortController();
    this.#inspection = pending;
    this.publish({ ...this.#state, selected: null, inspecting: true, failure: null });
    try {
      const result = await this.actions.loadTaxOpeningRevision.execute(
        this.access,
        this.employee,
        this.year,
        summary.version,
        pending.signal,
      );
      if (pending.signal.aborted || this.#inspection !== pending) return;
      if (result.ok) {
        if (
          result.value.id !== summary.id ||
          result.value.version !== summary.version ||
          result.value.status !== summary.status
        )
          this.fail({ code: "invalid_response", fields: {}, parameters: {} });
        else this.publish({ ...this.#state, inspecting: false, selected: result.value });
      } else this.fail(result.failure);
    } catch {
      if (!pending.signal.aborted && this.#inspection === pending)
        this.fail({ code: "unexpected_error", fields: {}, parameters: {} });
    } finally {
      if (this.#inspection === pending) this.#inspection = null;
    }
  };
  closeInspection = (): void => {
    if (!this.#active) return;
    this.#inspection?.abort();
    this.#inspection = null;
    this.publish({ ...this.#state, selected: null, inspecting: false });
  };
  private fail(failure: Failure, unavailable = false): void {
    this.publish(
      unavailable || taxOpeningAccessLost(failure)
        ? { ...initialTaxOpeningState, stage: "unavailable", failure }
        : { ...this.#state, selected: null, inspecting: false, failure },
    );
  }
  private publish(state: TaxOpeningState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
