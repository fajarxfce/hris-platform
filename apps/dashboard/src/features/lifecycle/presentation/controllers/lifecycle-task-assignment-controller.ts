import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleTaskAssignment } from "../../domain/entities/lifecycle-task-assignment";
import type { LifecycleTaskContext } from "../../domain/entities/lifecycle-task-context";
import { canAssignLifecycleTask } from "../../domain/policies/lifecycle-task-assignment-policy";
import { lifecycleTaskMutationWasRejected } from "../../domain/policies/lifecycle-task-change-policy";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import {
  initialLifecycleTaskEditorState,
  type LifecycleTaskEditorState,
} from "../models/lifecycle-task-editor-state";

export type LifecycleTaskAssignmentFields = Pick<LifecycleTaskAssignment, "assigneeId" | "reason">;
type Submission = Readonly<{ operation: OperationId; input: LifecycleTaskAssignment }>;

export class LifecycleTaskAssignmentController {
  #state = initialLifecycleTaskEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  readonly assignable: boolean;
  constructor(
    private readonly change: LifecycleUseCases["assignTask"],
    private readonly access: CompanyAccess,
    private readonly context: LifecycleTaskContext,
    private readonly nextIdentifier: () => string,
  ) {
    this.assignable = canAssignLifecycleTask(access, context);
  }
  getSnapshot = (): LifecycleTaskEditorState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    this.#active = true;
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.#submission = null;
    this.publish(initialLifecycleTaskEditorState);
  };
  save = async (fields: LifecycleTaskAssignmentFields): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing" || !this.assignable) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        caseId: this.context.caseId,
        taskKey: this.context.task.key,
        expectedVersion: this.context.caseVersion,
        assigneeId: fields.assigneeId,
        reason: fields.reason,
      }),
    });
    await this.submit(false);
  };
  retry = async (): Promise<void> => {
    if (this.#active && this.#state.stage === "unconfirmed") await this.submit(true);
  };
  private async submit(wasUnconfirmed: boolean): Promise<void> {
    const submission = this.#submission;
    if (!submission) return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({
      ...this.#state,
      stage: "saving",
      failure: null,
      operationId: submission.operation,
    });
    try {
      let result: Result<MutationReceipt>;
      try {
        result = await this.change.execute(
          this.access,
          submission.operation,
          submission.input,
          pending.signal,
        );
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#submission = null;
        this.publish({
          stage: "saved",
          failure: null,
          operationId: null,
          receipt: result.value,
        });
      } else if (!wasUnconfirmed && lifecycleTaskMutationWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: [
            "stale_version",
            "data_conflict",
            "lifecycle_case_not_found",
            "lifecycle_task_not_found",
            "lifecycle_task_not_assigned",
            "lifecycle_case_not_open",
            "lifecycle_task_not_assignable",
          ].includes(result.failure.code)
            ? "conflict"
            : "editing",
          failure: result.failure,
          operationId: null,
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: LifecycleTaskEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
