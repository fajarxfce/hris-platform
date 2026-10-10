import type { AccountId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleTaskStatus } from "../../domain/entities/lifecycle-task";
import type { LifecycleTaskChange } from "../../domain/entities/lifecycle-task-change";
import type { LifecycleTaskContext } from "../../domain/entities/lifecycle-task-context";
import {
  availableLifecycleTaskStatuses,
  lifecycleTaskChangeWasRejected,
} from "../../domain/policies/lifecycle-task-change-policy";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import {
  initialLifecycleTaskEditorState,
  type LifecycleTaskEditorState,
} from "../models/lifecycle-task-editor-state";

export type LifecycleTaskFields = Pick<LifecycleTaskChange, "status" | "reason">;
type Submission = Readonly<{ operation: OperationId; input: LifecycleTaskChange }>;

export class LifecycleTaskEditorController {
  #state = initialLifecycleTaskEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  readonly statuses: readonly LifecycleTaskStatus[];
  constructor(
    private readonly change: LifecycleUseCases["changeTask"],
    private readonly access: CompanyAccess,
    account: AccountId,
    private readonly context: LifecycleTaskContext,
    private readonly nextIdentifier: () => string,
  ) {
    this.statuses = availableLifecycleTaskStatuses(access, account, context);
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
  save = async (fields: LifecycleTaskFields): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing" || this.statuses.length === 0) return;
    if (!this.statuses.includes(fields.status)) {
      this.publish({
        ...this.#state,
        failure: { code: "invalid_lifecycle_change", fields: {}, parameters: {} },
      });
      return;
    }
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        caseId: this.context.caseId,
        taskKey: this.context.task.key,
        expectedVersion: this.context.caseVersion,
        status: fields.status,
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
      } else if (!wasUnconfirmed && lifecycleTaskChangeWasRejected(result.failure)) {
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
            "lifecycle_task_unchanged",
            "lifecycle_task_cannot_be_waived",
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
