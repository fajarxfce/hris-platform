import type { AccountId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import { canAssignLifecycleTask } from "../../domain/policies/lifecycle-task-assignment-policy";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import {
  type AssignedLifecycleTasksState,
  initialAssignedLifecycleTasksState,
} from "../models/assigned-lifecycle-tasks-state";

export class AssignedLifecycleTasksController {
  #state = initialAssignedLifecycleTasksState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LifecycleUseCases["loadAssignedTasks"],
    private readonly access: CompanyAccess,
    private readonly account: AccountId,
    private readonly after: string | null,
  ) {}
  getSnapshot = (): AssignedLifecycleTasksState => this.#state;
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
    this.publish(initialAssignedLifecycleTasksState);
  };
  openTask = (key: string): void => {
    const task = this.#state.page?.items.find((item) => `${item.caseId}:${item.task.key}` === key);
    if (this.#active && this.#state.stage === "ready" && !this.#state.selectedTask && task)
      this.publish({ ...this.#state, selectedTask: task });
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
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#state.selectedTask) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialAssignedLifecycleTasksState);
    try {
      const result = await this.load.execute(this.access, this.account, this.after, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish({
        ...initialAssignedLifecycleTasksState,
        ...(result.ok
          ? ({ stage: "ready", page: result.value } as const)
          : ({ stage: "unavailable", failure: result.failure } as const)),
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialAssignedLifecycleTasksState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: AssignedLifecycleTasksState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
