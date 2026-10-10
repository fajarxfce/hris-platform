import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeImportAction } from "../../domain/entities/employee-import-change";
import {
  employeeImportChangeWasRejected,
  employeeImportReviewChanged,
} from "../../domain/policies/employee-import-change-policy";
import { validateEmployeeImportReview } from "../../domain/policies/employee-import-review-policy";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import {
  type EmployeeImportTransitionState,
  initialEmployeeImportTransitionState,
} from "../models/employee-import-transition-state";

type Submission = Readonly<{
  operation: OperationId;
  perform: (signal: AbortSignal) => Promise<Result<MutationReceipt>>;
}>;
export class EmployeeImportTransitionController {
  #state = initialEmployeeImportTransitionState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      PeopleUseCases,
      "loadEmployeeImport" | "applyEmployeeImport" | "resumeEmployeeImport" | "cancelEmployeeImport"
    >,
    private readonly access: CompanyAccess,
    private readonly id: string,
    readonly action: EmployeeImportAction,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): EmployeeImportTransitionState => this.#state;
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
    this.publish(initialEmployeeImportTransitionState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialEmployeeImportTransitionState);
    try {
      const result = await this.actions.loadEmployeeImport.execute(
        this.access,
        this.id,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialEmployeeImportTransitionState, stage: "reviewing", review: result.value }
          : {
              ...initialEmployeeImportTransitionState,
              stage: "unavailable",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialEmployeeImportTransitionState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  apply = async (reason: string, allowPartial: boolean): Promise<void> => {
    const review = this.#state.review;
    if (!this.#active || this.action !== "apply" || this.#state.stage !== "reviewing" || !review)
      return;
    const ready = validateEmployeeImportReview(this.access, review, "apply", allowPartial);
    if (!ready.ok) {
      this.publish({ ...this.#state, failure: ready.failure });
      return;
    }
    const operation = this.nextIdentifier() as OperationId;
    const input = Object.freeze({
      importId: review.batch.id,
      expectedVersion: review.batch.version,
      reason,
      allowPartial,
    });
    this.#submission = Object.freeze({
      operation,
      perform: (signal: AbortSignal) =>
        this.actions.applyEmployeeImport.execute(this.access, operation, input, signal),
    });
    await this.submit(false);
  };
  resume = async (reason: string): Promise<void> => {
    const review = this.#state.review;
    if (!this.#active || this.action !== "resume" || this.#state.stage !== "reviewing" || !review)
      return;
    const ready = validateEmployeeImportReview(this.access, review, "resume");
    if (!ready.ok) {
      this.publish({ ...this.#state, failure: ready.failure });
      return;
    }
    const operation = this.nextIdentifier() as OperationId;
    const input = Object.freeze({
      importId: review.batch.id,
      expectedVersion: review.batch.version,
      reason,
    });
    this.#submission = Object.freeze({
      operation,
      perform: (signal: AbortSignal) =>
        this.actions.resumeEmployeeImport.execute(this.access, operation, input, signal),
    });
    await this.submit(false);
  };
  cancel = async (reason: string): Promise<void> => {
    const review = this.#state.review;
    if (!this.#active || this.action !== "cancel" || this.#state.stage !== "reviewing" || !review)
      return;
    const ready = validateEmployeeImportReview(this.access, review, "cancel");
    if (!ready.ok) {
      this.publish({ ...this.#state, failure: ready.failure });
      return;
    }
    const operation = this.nextIdentifier() as OperationId;
    const input = Object.freeze({
      importId: review.batch.id,
      expectedVersion: review.batch.version,
      reason,
    });
    this.#submission = Object.freeze({
      operation,
      perform: (signal: AbortSignal) =>
        this.actions.cancelEmployeeImport.execute(this.access, operation, input, signal),
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
      operationId: submission.operation,
      failure: null,
    });
    try {
      let result: Result<MutationReceipt>;
      try {
        result = await submission.perform(pending.signal);
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: "saved",
          review: null,
          receipt: result.value,
          failure: null,
          operationId: null,
        });
      } else if (!wasUnconfirmed && employeeImportChangeWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: employeeImportReviewChanged(result.failure) ? "conflict" : "reviewing",
          failure: result.failure,
          operationId: null,
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: EmployeeImportTransitionState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
