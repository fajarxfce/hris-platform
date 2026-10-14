import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { type CompensationState, initialCompensationState } from "../models/compensation-state";

export class CompensationController {
  #active = false;
  #pending: AbortController | null = null;
  #state = initialCompensationState;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: PayrollUseCases["loadCompensationReview"],
    private readonly access: CompanyAccess,
    private readonly employee: string,
    private readonly month: string,
    private readonly after: string | null,
  ) {}
  getSnapshot = (): CompensationState => this.#state;
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
    this.publish(initialCompensationState);
  };
  selectRevision = (revision: string): void => {
    if (!this.#active || this.#state.stage !== "ready") return;
    const evidence = this.#state.review?.history.items.find(
      (item) => String(item.compensation.appliedRevision) === revision,
    );
    if (evidence)
      this.publish({ ...this.#state, selected: { compensation: evidence.compensation, evidence } });
  };
  inspectConfiguration = (): void => {
    const current = this.#state.review?.current;
    if (!this.#active || this.#state.stage !== "ready" || !current) return;
    this.publish({
      ...this.#state,
      selected: {
        compensation: current,
        evidence:
          this.#state.review?.history.items.find(
            (item) => item.compensation.appliedRevision === current.appliedRevision,
          ) ?? null,
      },
    });
  };
  closeInspection = (): void => {
    if (this.#active) this.publish({ ...this.#state, selected: null });
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialCompensationState);
    try {
      const result = await this.load.execute(
        this.access,
        this.employee,
        this.month,
        this.after,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialCompensationState, stage: "ready", review: result.value }
          : { ...initialCompensationState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialCompensationState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: CompensationState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
