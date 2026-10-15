import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LoadEmployee } from "../../../people/domain/usecases/load-employee";
import { canReadOnboardingSchedules } from "../../domain/policies/onboarding-schedule-policy";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import {
  initialOnboardingSchedulesState,
  type OnboardingSchedulesState,
} from "../models/onboarding-schedule-state";
export class OnboardingSchedulesController {
  #state = initialOnboardingSchedulesState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly loadEmployee: Pick<LoadEmployee, "execute">,
    private readonly load: LifecycleUseCases["loadOnboardingSchedules"],
    private readonly access: CompanyAccess,
    private readonly employeeId: string,
    private readonly asOf: string,
  ) {}
  getSnapshot = (): OnboardingSchedulesState => this.#state;
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
    this.publish(initialOnboardingSchedulesState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialOnboardingSchedulesState);
    try {
      if (!canReadOnboardingSchedules(this.access.permissions)) {
        this.publish({
          ...initialOnboardingSchedulesState,
          stage: "unavailable",
          failure: { code: "onboarding_access_required", fields: {}, parameters: {} },
        });
        return;
      }
      const [employee, schedules] = await Promise.all([
        this.loadEmployee.execute(this.access, this.employeeId, this.asOf, pending.signal),
        this.load.execute(this.access, this.employeeId, pending.signal),
      ]);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        !employee.ok
          ? { ...initialOnboardingSchedulesState, stage: "unavailable", failure: employee.failure }
          : !schedules.ok
            ? {
                ...initialOnboardingSchedulesState,
                stage: "unavailable",
                failure: schedules.failure,
              }
            : { stage: "ready", employee: employee.value, items: schedules.value, failure: null },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialOnboardingSchedulesState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) {
        pending.abort();
        this.#pending = null;
      }
    }
  };
  private publish(state: OnboardingSchedulesState): void {
    this.#state = state;
    for (const listener of [...this.#listeners]) listener();
  }
}
