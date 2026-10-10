import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OffboardingCompletion } from "../../domain/entities/offboarding-completion";
import {
  offboardingCompletionWasRejected,
  validateOffboardingReview,
} from "../../domain/policies/offboarding-policy";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import { initialOffboardingState, type OffboardingState } from "../models/offboarding-state";

type Submission = Readonly<{ operation: OperationId; input: OffboardingCompletion }>;
export class OffboardingController {
  #state = initialOffboardingState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      LifecycleUseCases,
      "loadOffboardingReview" | "completeOffboarding"
    >,
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): OffboardingState => this.#state;
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
    this.publish(initialOffboardingState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialOffboardingState);
    try {
      const result = await this.actions.loadOffboardingReview.execute(
        this.access,
        this.id,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialOffboardingState, stage: "reviewing", review: result.value }
          : { ...initialOffboardingState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialOffboardingState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  complete = async (reason: string): Promise<void> => {
    const review = this.#state.review;
    if (!this.#active || this.#state.stage !== "reviewing" || !review) return;
    const ready = validateOffboardingReview(review);
    if (!ready.ok) {
      this.publish({ ...this.#state, failure: ready.failure });
      return;
    }
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        caseId: review.case.id,
        expectedVersion: review.case.version,
        employmentVersion: review.employmentVersion,
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
        result = await this.actions.completeOffboarding.execute(
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
          stage: "completed",
          receipt: result.value,
          failure: null,
          operationId: null,
        });
      } else if (!wasUnconfirmed && offboardingCompletionWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          failure: result.failure,
          operationId: null,
          stage: [
            "stale_version",
            "stale_employment_version",
            "data_conflict",
            "lifecycle_case_not_found",
            "lifecycle_case_not_open",
            "employee_not_found",
            "required_lifecycle_tasks_pending",
            "lifecycle_tasks_unresolved",
            "offboarding_terms_changed",
            "other_lifecycle_cases_pending",
            "offboarding_date_not_reached",
            "cannot_complete_own_offboarding",
            "scheduled_employment_changes_pending",
            "reporting_reassignment_required",
            "last_company_administrator",
          ].includes(result.failure.code)
            ? "conflict"
            : "reviewing",
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: OffboardingState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
