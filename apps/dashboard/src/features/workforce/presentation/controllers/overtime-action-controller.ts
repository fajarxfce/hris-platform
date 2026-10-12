import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  type OvertimeActionSnapshot,
  overtimeActionSnapshot,
} from "../../domain/entities/overtime-action-snapshot";
import type { OvertimeActualTime } from "../../domain/entities/overtime-actual-time";
import type { OvertimeAction } from "../../domain/entities/overtime-request-details";
import {
  overtimeActionReadDenied,
  overtimeActionReviewChanged,
  overtimeActionWasRejected,
} from "../../domain/policies/overtime-action-policy";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import {
  initialOvertimeActionState,
  type OvertimeActionState,
} from "../models/overtime-action-state";

type PerformSubmission = (signal: AbortSignal) => Promise<Result<MutationReceipt>>;
type Submission = Readonly<{ operation: OperationId; perform: PerformSubmission }>;

export class OvertimeActionController {
  #state = initialOvertimeActionState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();

  constructor(
    private readonly actions: Pick<
      WorkforceUseCases,
      | "reviewOvertimeAction"
      | "submitOvertimeActual"
      | "decideOvertimeRequest"
      | "withdrawOvertimeRequest"
    >,
    private readonly access: CompanyAccess,
    private readonly id: string,
    readonly action: OvertimeAction,
    private readonly nextIdentifier: () => string,
  ) {}

  getSnapshot = (): OvertimeActionState => this.#state;
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
    this.publish(initialOvertimeActionState);
  };

  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialOvertimeActionState);
    try {
      const result = await this.actions.reviewOvertimeAction.execute(
        this.access,
        this.id,
        this.action,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialOvertimeActionState, stage: "reviewing", review: result.value }
          : { ...initialOvertimeActionState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialOvertimeActionState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };

  submitActual = (input: OvertimeActualTime, reason: string): Promise<void> => {
    const actual = Object.freeze({
      startsAt: input.startsAt,
      endsAt: input.endsAt,
      breakMinutes: input.breakMinutes,
    });
    return this.startSubmission(
      "SUBMIT_ACTUAL",
      (operation, review) => (signal) =>
        this.actions.submitOvertimeActual.execute(
          this.access,
          operation,
          review,
          actual,
          reason,
          signal,
        ),
    );
  };
  approve = (reason: string): Promise<void> =>
    this.startSubmission(
      "APPROVE",
      (operation, review) => (signal) =>
        this.actions.decideOvertimeRequest.execute(
          this.access,
          operation,
          review,
          "APPROVE",
          reason,
          signal,
        ),
    );
  reject = (reason: string): Promise<void> =>
    this.startSubmission(
      "REJECT",
      (operation, review) => (signal) =>
        this.actions.decideOvertimeRequest.execute(
          this.access,
          operation,
          review,
          "REJECT",
          reason,
          signal,
        ),
    );
  withdraw = (reason: string): Promise<void> =>
    this.startSubmission(
      "WITHDRAW",
      (operation, review) => (signal) =>
        this.actions.withdrawOvertimeRequest.execute(
          this.access,
          operation,
          review,
          reason,
          signal,
        ),
    );
  retry = async (): Promise<void> => {
    if (this.#active && this.#state.stage === "unconfirmed") await this.submit(true);
  };

  /** Own one immutable command until acknowledged; a read cannot replace its observed facts. */
  private async startSubmission(
    action: OvertimeAction,
    prepare: (operation: OperationId, review: OvertimeActionSnapshot) => PerformSubmission,
  ): Promise<void> {
    const current = this.#state.review;
    if (!this.#active || this.action !== action || this.#state.stage !== "reviewing" || !current)
      return;
    const operation = this.nextIdentifier() as OperationId;
    this.#submission = Object.freeze({
      operation,
      perform: prepare(operation, overtimeActionSnapshot(current)),
    });
    await this.submit(false);
  }

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
        result = await submission.perform(pending.signal);
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#submission = null;
        this.publish({ ...initialOvertimeActionState, stage: "saved", receipt: result.value });
        return;
      }
      const review = overtimeActionReadDenied(result.failure) ? null : this.#state.review;
      if (!wasUnconfirmed && overtimeActionWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          review,
          failure: result.failure,
          operationId: null,
          stage: !review
            ? "unavailable"
            : overtimeActionReviewChanged(result.failure)
              ? "conflict"
              : "reviewing",
        });
      } else {
        this.publish({ ...this.#state, review, stage: "unconfirmed", failure: result.failure });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }

  private publish(state: OvertimeActionState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
