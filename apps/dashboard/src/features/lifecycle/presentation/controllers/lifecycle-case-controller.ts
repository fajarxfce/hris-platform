import type { Failure } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { lifecycleCaseActions } from "../../domain/policies/lifecycle-case-change-policy";
import { canAssignLifecycleTask } from "../../domain/policies/lifecycle-task-assignment-policy";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { initialLifecycleCaseState, type LifecycleCaseState } from "../models/lifecycle-case-state";

export class LifecycleCaseController {
  #state = initialLifecycleCaseState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LifecycleUseCases["loadCase"],
    private readonly access: CompanyAccess,
    private readonly id: string,
  ) {}
  getSnapshot = (): LifecycleCaseState => this.#state;
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
    this.publish(initialLifecycleCaseState);
  };
  openTask = (key: string): void => {
    const details = this.#state.case;
    const task = details?.tasks.find((task) => task.key === key);
    if (
      !this.#active ||
      this.#state.stage !== "ready" ||
      this.#state.selectedTask ||
      this.#state.caseAction ||
      !details ||
      !task
    )
      return;
    this.publish({
      ...this.#state,
      selectedTask: Object.freeze({
        companyId: details.companyId,
        caseId: details.id,
        caseVersion: details.version,
        caseStatus: details.status,
        employee: details.employee,
        kind: details.kind,
        task,
      }),
    });
  };
  openTaskAssignment = (): void => {
    if (
      this.#active &&
      this.#state.selectedTask &&
      canAssignLifecycleTask(this.access, this.#state.selectedTask)
    )
      this.publish({ ...this.#state, taskPanel: "assignment" });
  };
  showTaskDetails = (): void => {
    if (this.#active && this.#state.selectedTask)
      this.publish({ ...this.#state, taskPanel: "status" });
  };
  closeTask = (): void => {
    if (this.#active) this.publish({ ...this.#state, selectedTask: null, taskPanel: "status" });
  };
  openCancellation = (): void => {
    const details = this.#state.case;
    if (
      !this.#active ||
      this.#state.stage !== "ready" ||
      this.#state.selectedTask ||
      this.#state.caseAction ||
      !details ||
      !lifecycleCaseActions(this.access, details).cancel
    )
      return;
    this.publish({ ...this.#state, caseAction: "cancel" });
  };
  openOnboardingCompletion = (): void => {
    const details = this.#state.case;
    if (
      !this.#active ||
      this.#state.stage !== "ready" ||
      this.#state.selectedTask ||
      this.#state.caseAction ||
      !details ||
      !lifecycleCaseActions(this.access, details).completeOnboarding
    )
      return;
    this.publish({ ...this.#state, caseAction: "completeOnboarding" });
  };
  closeCaseAction = (): void => {
    if (this.#active) this.publish({ ...this.#state, caseAction: null });
  };
  /** History uses the same case-read grant; its revocation also invalidates the overview. */
  reportScopeFailure = (failure: Failure): void => {
    if (
      !this.#active ||
      ![
        "access_denied",
        "company_access_denied",
        "authentication_required",
        "session_revoked",
        "unauthenticated",
        "mfa_required",
        "mfa_setup_required",
        "lifecycle_case_not_found",
      ].includes(failure.code)
    )
      return;
    this.#pending?.abort();
    this.#pending = null;
    this.publish({ ...initialLifecycleCaseState, stage: "unavailable", failure });
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#state.selectedTask || this.#state.caseAction) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialLifecycleCaseState);
    try {
      const result = await this.load.execute(this.access, this.id, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish({
        ...initialLifecycleCaseState,
        ...(result.ok
          ? ({ stage: "ready", case: result.value } as const)
          : ({ stage: "unavailable", failure: result.failure } as const)),
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialLifecycleCaseState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: LifecycleCaseState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
