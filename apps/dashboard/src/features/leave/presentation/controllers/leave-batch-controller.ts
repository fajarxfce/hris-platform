import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { leaveBatchAccessLost } from "../../domain/policies/leave-batch-policy";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { initialLeaveBatchState, type LeaveBatchState } from "../models/leave-batch-state";
/** Owns one result page, one request and at most 30 delayed progress reads. */
export class LeaveBatchController {
  #state = initialLeaveBatchState;
  #active = false;
  #visible = true;
  #pending: AbortController | null = null;
  #timer: ReturnType<typeof setTimeout> | null = null;
  #remainingUpdates = 30;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LeaveUseCases["loadBatch"],
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly after: string | null,
  ) {}
  getSnapshot = (): LeaveBatchState => this.#state;
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
    this.publish(initialLeaveBatchState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.stopObservation();
    this.#remainingUpdates = 30;
    this.publish({ ...initialLeaveBatchState, details: this.#state.details });
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
      const result = await this.load.execute(this.access, this.id, this.after, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialLeaveBatchState, stage: "ready", details: result.value }
          : {
              ...this.#state,
              stage: "unavailable",
              failure: result.failure,
              details:
                leaveBatchAccessLost(result.failure) ||
                result.failure.code === "leave_batch_not_found"
                  ? null
                  : this.#state.details,
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
    const details = this.#state.details;
    if (
      !this.#active ||
      !this.#visible ||
      this.#pending ||
      this.#timer ||
      this.#state.failure ||
      !details ||
      !["QUEUED", "RUNNING"].includes(details.job.status)
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
  private publish(state: LeaveBatchState): void {
    this.#state = state;
    for (const listener of [...this.#listeners]) listener();
  }
}
