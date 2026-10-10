import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleCase } from "../../domain/entities/lifecycle-case";
import type {
  LifecycleCaseAction,
  LifecycleCaseChange,
} from "../../domain/entities/lifecycle-case-change";
import {
  lifecycleCaseActions,
  lifecycleCaseChangeWasRejected,
} from "../../domain/policies/lifecycle-case-change-policy";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import {
  initialLifecycleCaseActionState,
  type LifecycleCaseActionState,
} from "../models/lifecycle-case-action-state";

type Submission = Readonly<{ operation: OperationId; input: LifecycleCaseChange }>;
export class LifecycleCaseActionController {
  #state = initialLifecycleCaseActionState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  readonly allowed: boolean;
  constructor(
    private readonly change:
      | LifecycleUseCases["cancelCase"]
      | LifecycleUseCases["completeOnboarding"],
    private readonly access: CompanyAccess,
    private readonly details: LifecycleCase,
    action: LifecycleCaseAction,
    private readonly nextIdentifier: () => string,
  ) {
    this.allowed = lifecycleCaseActions(access, details)[action];
  }
  getSnapshot = (): LifecycleCaseActionState => this.#state;
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
    this.publish(initialLifecycleCaseActionState);
  };
  save = async (reason: string): Promise<void> => {
    if (!this.#active || !this.allowed || this.#state.stage !== "editing") return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        caseId: this.details.id,
        expectedVersion: this.details.version,
        reason,
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
        this.publish({ stage: "saved", receipt: result.value, failure: null, operationId: null });
      } else if (!wasUnconfirmed && lifecycleCaseChangeWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          operationId: null,
          failure: result.failure,
          stage: [
            "stale_version",
            "data_conflict",
            "lifecycle_case_not_found",
            "lifecycle_case_not_open",
            "required_lifecycle_tasks_pending",
            "lifecycle_tasks_unresolved",
          ].includes(result.failure.code)
            ? "conflict"
            : "editing",
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: LifecycleCaseActionState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
