import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { leaveCarryExpiryAccessLost } from "../../domain/policies/leave-carry-expiry-policy";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import {
  initialLeaveCarryExpiryState,
  type LeaveCarryExpiryState,
} from "../models/leave-carry-expiry-state";
/** Owns one result page, one request and at most 30 delayed progress reads. */
export class LeaveCarryExpiryController {
  #state = initialLeaveCarryExpiryState;
  #active = false;
  #visible = true;
  #pending: AbortController | null = null;
  #timer: ReturnType<typeof setTimeout> | null = null;
  #remainingUpdates = 30;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LeaveUseCases["loadCarryExpiry"],
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly after: string | null,
    private readonly now: () => number = Date.now,
  ) {}
  getSnapshot = (): LeaveCarryExpiryState => this.#state;
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
    this.publish(initialLeaveCarryExpiryState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.stopObservation();
    this.#remainingUpdates = 30;
    this.publish({ ...initialLeaveCarryExpiryState, details: this.#state.details });
    if (this.#visible) await this.read(false);
  };
  setVisible = (visible: boolean): void => {
    if (this.#visible === visible) return;
    this.#visible = visible;
    if (!visible) {
      this.stopObservation();
      if (this.#state.stage === "updating") this.publish({ ...this.#state, stage: "ready" });
    } else if (this.#active) {
      void this.read(false);
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
          ? { ...initialLeaveCarryExpiryState, stage: "ready", details: result.value }
          : {
              ...this.#state,
              stage: "unavailable",
              failure: result.failure,
              details:
                leaveCarryExpiryAccessLost(result.failure) ||
                result.failure.code === "leave_carry_expiry_not_found"
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
    const untilDue =
      Math.max(Date.parse(details.job.scheduledFor), Date.parse(details.job.availableAt)) -
      this.now();
    if (details.job.status === "QUEUED" && untilDue > 120_000) return;
    if (this.#remainingUpdates === 0) {
      this.publish({ ...this.#state, updatesPaused: true });
      return;
    }
    this.#timer = setTimeout(
      () => {
        this.#timer = null;
        this.#remainingUpdates -= 1;
        void this.read(true);
      },
      details.job.status === "QUEUED" ? Math.max(4000, untilDue) : 4000,
    );
  }
  private stopObservation(): void {
    if (this.#timer !== null) clearTimeout(this.#timer);
    this.#timer = null;
    this.#pending?.abort();
    this.#pending = null;
  }
  private publish(state: LeaveCarryExpiryState): void {
    this.#state = state;
    for (const listener of [...this.#listeners]) listener();
  }
}
