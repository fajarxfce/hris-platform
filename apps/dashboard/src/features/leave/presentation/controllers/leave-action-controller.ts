import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  LeaveActionSnapshot,
  LeaveRequestIntent,
} from "../../domain/entities/leave-request-action";
import {
  leaveActionReviewChanged,
  leaveActionWasRejected,
} from "../../domain/policies/leave-request-action-policy";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { initialLeaveActionState, type LeaveActionState } from "../models/leave-action-state";

type Submission = Readonly<{
  operation: OperationId;
  perform: (signal: AbortSignal) => Promise<Result<MutationReceipt>>;
}>;
export class LeaveActionController {
  #state = initialLeaveActionState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      LeaveUseCases,
      "reviewAction" | "decide" | "withdraw" | "requestCancellation"
    >,
    private readonly access: CompanyAccess,
    private readonly id: string,
    readonly intent: LeaveRequestIntent,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): LeaveActionState => this.#state;
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
    this.publish(initialLeaveActionState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialLeaveActionState);
    try {
      const result = await this.actions.reviewAction.execute(
        this.access,
        this.id,
        this.intent,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? {
              ...initialLeaveActionState,
              stage: "reviewing",
              review: result.value,
              phase: result.value.status === "CANCELLATION_PENDING" ? "cancellation" : "request",
            }
          : { ...initialLeaveActionState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialLeaveActionState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  confirm = async (reason: string): Promise<void> => {
    const current = this.#state.review;
    if (!this.#active || this.#state.stage !== "reviewing" || !current) return;
    const review: LeaveActionSnapshot = Object.freeze({
      id: current.id,
      companyId: current.companyId,
      version: current.version,
      status: current.status,
      availableActions: Object.freeze([...current.availableActions]),
    });
    const operation = this.nextIdentifier() as OperationId;
    this.#submission = Object.freeze({
      operation,
      perform: {
        approve: (signal: AbortSignal) =>
          this.actions.decide.execute(this.access, operation, review, "APPROVE", reason, signal),
        reject: (signal: AbortSignal) =>
          this.actions.decide.execute(this.access, operation, review, "REJECT", reason, signal),
        withdraw: (signal: AbortSignal) =>
          this.actions.withdraw.execute(this.access, operation, review, reason, signal),
        cancel: (signal: AbortSignal) =>
          this.actions.requestCancellation.execute(this.access, operation, review, reason, signal),
      }[this.intent],
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
          failure: null,
          operationId: null,
          receipt: result.value,
        });
      } else {
        const review = [
          "access_denied",
          "leave_request_not_found",
          "company_access_denied",
        ].includes(result.failure.code)
          ? null
          : this.#state.review;
        if (!wasUnconfirmed && leaveActionWasRejected(result.failure)) {
          this.#submission = null;
          this.publish({
            ...this.#state,
            review,
            failure: result.failure,
            operationId: null,
            stage: !review
              ? "unavailable"
              : leaveActionReviewChanged(result.failure)
                ? "conflict"
                : "reviewing",
          });
        } else
          this.publish({ ...this.#state, review, stage: "unconfirmed", failure: result.failure });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: LeaveActionState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
