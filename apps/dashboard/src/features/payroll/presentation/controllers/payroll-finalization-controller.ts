import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { payrollFinalizationAccessLost } from "../../domain/policies/payroll-finalization-policy";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialPayrollFinalizationState,
  type PayrollFinalizationState,
} from "../models/payroll-finalization-state";
/** One visible publication owns its current detail, one request and up to 30 delayed progress reads. */
export class PayrollFinalizationController {
  #state = initialPayrollFinalizationState;
  #active = false;
  #visible = true;
  #pending: AbortController | null = null;
  #timer: ReturnType<typeof setTimeout> | null = null;
  #remainingUpdates = 30;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: PayrollUseCases["loadFinalization"],
    private readonly access: CompanyAccess,
    private readonly id: string,
  ) {}
  getSnapshot = (): PayrollFinalizationState => this.#state;
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
    this.stopObservation();
    this.publish(initialPayrollFinalizationState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.stopObservation();
    this.#remainingUpdates = 30;
    this.publish({ ...initialPayrollFinalizationState, details: this.#state.details });
    if (this.#visible) await this.read(false);
  };
  setVisible = (visible: boolean): void => {
    if (this.#visible === visible) return;
    this.#visible = visible;
    if (!visible) {
      this.stopObservation();
      if (this.#state.stage === "updating") this.publish({ ...this.#state, stage: "ready" });
    } else if (this.#active) {
      if (this.#state.stage === "loading") void this.read(false);
      else this.scheduleUpdate();
    }
  };
  private async read(automatic: boolean): Promise<void> {
    if (!this.#active || !this.#visible || this.#pending) return;
    const pending = new AbortController();
    this.#pending = pending;
    if (automatic) this.publish({ ...this.#state, stage: "updating" });
    try {
      const result = await this.load.execute(this.access, this.id, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialPayrollFinalizationState, stage: "ready", details: result.value }
          : {
              ...this.#state,
              stage: "unavailable",
              details: payrollFinalizationAccessLost(result.failure) ? null : this.#state.details,
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...this.#state,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) {
        this.#pending = null;
        this.scheduleUpdate();
      }
    }
  }
  private scheduleUpdate(): void {
    if (
      !this.#active ||
      !this.#visible ||
      this.#pending ||
      this.#timer ||
      this.#state.failure ||
      !this.#state.details ||
      !["QUEUED", "RUNNING"].includes(this.#state.details.job.status)
    )
      return;
    if (this.#remainingUpdates === 0) {
      this.publish({ ...this.#state, updatesPaused: true });
      return;
    }
    this.#timer = setTimeout(() => {
      this.#timer = null;
      this.#remainingUpdates -= 1;
      void this.read(true);
    }, 4000);
  }
  private stopObservation(): void {
    if (this.#timer !== null) clearTimeout(this.#timer);
    this.#timer = null;
    this.#pending?.abort();
    this.#pending = null;
  }
  private publish(state: PayrollFinalizationState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
