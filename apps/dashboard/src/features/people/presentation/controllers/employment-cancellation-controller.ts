import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmploymentCancellation } from "../../domain/entities/employment-cancellation";
import { employmentCancellationWasRejected } from "../../domain/policies/employment-cancellation-policy";
import { canManageEmployment } from "../../domain/policies/employment-policy";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import {
  type EmploymentCancellationState,
  initialEmploymentCancellationState,
} from "../models/employment-cancellation-state";

type Submission = Readonly<{ operation: OperationId; input: EmploymentCancellation }>;
export class EmploymentCancellationController {
  #state = initialEmploymentCancellationState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      PeopleUseCases,
      "loadEmploymentRevision" | "cancelEmploymentRevision"
    >,
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly revision: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): EmploymentCancellationState => this.#state;
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
    this.#submission = null;
    this.publish(initialEmploymentCancellationState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    this.#pending = null;
    this.publish(initialEmploymentCancellationState);
    if (!canManageEmployment(this.access.permissions)) {
      this.publish({
        ...initialEmploymentCancellationState,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
      return;
    }
    const pending = new AbortController();
    this.#pending = pending;
    try {
      const result = await this.actions.loadEmploymentRevision.execute(
        this.access,
        this.id,
        this.revision,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialEmploymentCancellationState, stage: "reviewing", details: result.value }
          : {
              ...initialEmploymentCancellationState,
              stage: "unavailable",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialEmploymentCancellationState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  cancelRevision = async (reason: string): Promise<void> => {
    const details = this.#state.details;
    if (!this.#active || this.#state.stage !== "reviewing" || !details?.canCancel) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        employeeId: details.employeeId,
        revision: details.revision.revision,
        expectedVersion: details.version,
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
      stage: "submitting",
      failure: null,
      operationId: submission.operation,
    });
    try {
      let result: Result<MutationReceipt>;
      try {
        result = await this.actions.cancelEmploymentRevision.execute(
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
          ...this.#state,
          stage: "cancelled",
          receipt: result.value,
          failure: null,
          operationId: null,
        });
      } else if (!wasUnconfirmed && employmentCancellationWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          failure: result.failure,
          operationId: null,
          stage: [
            "stale_version",
            "revision_already_cancelled",
            "effective_revision_cannot_be_cancelled",
          ].includes(result.failure.code)
            ? "conflict"
            : "reviewing",
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: EmploymentCancellationState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
