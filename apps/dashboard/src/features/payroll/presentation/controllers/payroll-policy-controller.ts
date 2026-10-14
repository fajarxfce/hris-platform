import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import { initialPayrollPolicyState, type PayrollPolicyState } from "../models/payroll-policy-state";

export class PayrollPolicyController {
  #active = false;
  #pending: AbortController | null = null;
  #state = initialPayrollPolicyState;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: PayrollUseCases["loadPolicy"],
    private readonly access: CompanyAccess,
    private readonly month: string,
    private readonly after: string | null,
  ) {}
  getSnapshot = (): PayrollPolicyState => this.#state;
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
    this.publish(initialPayrollPolicyState);
  };
  selectRevision = (revision: string): void => {
    if (!this.#active || this.#state.stage !== "ready") return;
    const evidence = this.#state.review?.history.items.find(
      (item) => String(item.policy.appliedRevision) === revision,
    );
    if (evidence) this.publish({ ...this.#state, selected: { policy: evidence.policy, evidence } });
  };
  inspectConfiguration = (): void => {
    const current = this.#state.review?.current;
    if (!this.#active || this.#state.stage !== "ready" || !current) return;
    this.publish({
      ...this.#state,
      selected: {
        policy: current,
        evidence:
          this.#state.review?.history.items.find(
            (item) => item.policy.appliedRevision === current.appliedRevision,
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
    this.publish(initialPayrollPolicyState);
    try {
      const result = await this.load.execute(this.access, this.month, this.after, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialPayrollPolicyState, stage: "ready", review: result.value }
          : { ...initialPayrollPolicyState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialPayrollPolicyState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: PayrollPolicyState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
