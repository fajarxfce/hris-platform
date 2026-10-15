import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { offboardingScheduleAccessLost } from "../../domain/policies/offboarding-schedule-policy";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import {
  initialOffboardingScheduleState,
  type OffboardingScheduleState,
} from "../models/offboarding-schedule-state";
/** One visible schedule owns one request and at most 30 delayed progress reads. Future schedules are read on demand. */
export class OffboardingScheduleController {
  #state = initialOffboardingScheduleState;
  #active = false;
  #visible = true;
  #pending: AbortController | null = null;
  #timer: ReturnType<typeof setTimeout> | null = null;
  #remainingUpdates = 30;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LifecycleUseCases["loadOffboardingSchedule"],
    private readonly access: CompanyAccess,
    private readonly caseId: string,
    private readonly id: string,
    private readonly now: () => number = Date.now,
  ) {}
  getSnapshot = (): OffboardingScheduleState => this.#state;
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
    this.publish(initialOffboardingScheduleState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.stopObservation();
    this.#remainingUpdates = 30;
    this.publish({ ...initialOffboardingScheduleState, details: this.#state.details });
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
      const result = await this.load.execute(this.access, this.caseId, this.id, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialOffboardingScheduleState, stage: "ready", details: result.value }
          : {
              ...this.#state,
              stage: "unavailable",
              failure: result.failure,
              details:
                offboardingScheduleAccessLost(result.failure) ||
                result.failure.code === "offboarding_schedule_not_found"
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
      details.status !== "SCHEDULED" ||
      !["QUEUED", "RUNNING"].includes(details.job.status) ||
      (!details.job.cancellationRequested && Date.parse(details.scheduledFor) > this.now())
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
  private publish(state: OffboardingScheduleState): void {
    this.#state = state;
    for (const listener of [...this.#listeners]) listener();
  }
}
