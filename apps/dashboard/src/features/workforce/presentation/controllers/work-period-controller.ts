import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { initialWorkPeriodState, type WorkPeriodState } from "../models/work-period-state";

/** One visible period owns one request and at most 30 delayed progress reads. */
export class WorkPeriodController {
  #state = initialWorkPeriodState;
  #active = false;
  #visible = true;
  #pending: AbortController | null = null;
  #timer: ReturnType<typeof setTimeout> | null = null;
  #remainingUpdates = 30;
  #listeners = new Set<() => void>();

  constructor(
    private readonly load: WorkforceUseCases["loadWorkPeriodOverview"],
    private readonly access: CompanyAccess,
    private readonly month: string,
  ) {}

  getSnapshot = (): WorkPeriodState => this.#state;
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
    this.publish(initialWorkPeriodState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.stopObservation();
    this.#remainingUpdates = 30;
    this.publish(initialWorkPeriodState);
    if (this.#visible) await this.readOverview(false);
  };
  setVisible = (visible: boolean): void => {
    if (this.#visible === visible) return;
    this.#visible = visible;
    if (!visible) {
      this.stopObservation();
      if (this.#state.stage === "updating") this.publish({ ...this.#state, stage: "ready" });
    } else if (this.#active) {
      if (this.#state.stage === "loading") void this.readOverview(false);
      else this.scheduleUpdate();
    }
  };

  private async readOverview(automatic: boolean): Promise<void> {
    if (!this.#active || !this.#visible || this.#pending) return;
    const pending = new AbortController();
    this.#pending = pending;
    if (automatic) this.publish({ ...this.#state, stage: "updating" });
    try {
      const result = await this.load.execute(this.access, this.month, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialWorkPeriodState, stage: "ready", overview: result.value }
          : { ...initialWorkPeriodState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialWorkPeriodState,
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
    const overview = this.#state.overview;
    if (
      !this.#active ||
      !this.#visible ||
      this.#pending ||
      this.#timer ||
      this.#state.failure ||
      overview?.period?.status !== "PROCESSING" ||
      !overview.attempt ||
      !["QUEUED", "RUNNING"].includes(overview.attempt.status)
    )
      return;
    if (this.#remainingUpdates === 0) {
      this.publish({ ...this.#state, updatesPaused: true });
      return;
    }
    this.#timer = setTimeout(() => {
      this.#timer = null;
      this.#remainingUpdates -= 1;
      void this.readOverview(true);
    }, 4000);
  }

  private stopObservation(): void {
    if (this.#timer !== null) clearTimeout(this.#timer);
    this.#timer = null;
    this.#pending?.abort();
    this.#pending = null;
  }
  private publish(state: WorkPeriodState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
