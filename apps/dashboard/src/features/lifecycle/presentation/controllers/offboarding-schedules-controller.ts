import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import {
  initialOffboardingSchedulesState,
  type OffboardingSchedulesState,
} from "../models/offboarding-schedule-state";
export class OffboardingSchedulesController {
  #state = initialOffboardingSchedulesState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<LifecycleUseCases, "loadOffboardingSchedules" | "loadCase">,
    private readonly access: CompanyAccess,
    private readonly caseId: string,
  ) {}
  getSnapshot = (): OffboardingSchedulesState => this.#state;
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
    this.publish(initialOffboardingSchedulesState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialOffboardingSchedulesState);
    try {
      const [details, schedules] = await Promise.all([
        this.actions.loadCase.execute(this.access, this.caseId, pending.signal),
        this.actions.loadOffboardingSchedules.execute(this.access, this.caseId, pending.signal),
      ]);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        !details.ok
          ? { ...initialOffboardingSchedulesState, stage: "unavailable", failure: details.failure }
          : !schedules.ok
            ? {
                ...initialOffboardingSchedulesState,
                stage: "unavailable",
                failure: schedules.failure,
              }
            : { stage: "ready", items: schedules.value, case: details.value, failure: null },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          items: null,
          case: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) {
        pending.abort();
        this.#pending = null;
      }
    }
  };
  private publish(state: OffboardingSchedulesState): void {
    this.#state = state;
    for (const listener of [...this.#listeners]) listener();
  }
}
