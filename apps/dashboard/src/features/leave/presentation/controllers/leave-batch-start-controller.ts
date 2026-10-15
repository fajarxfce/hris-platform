import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveBatchId, LeaveBatchKind } from "../../domain/entities/leave-batch";
import type { LeaveBatchCreation } from "../../domain/entities/leave-batch-command";
import type { LeaveBatchSelection } from "../../domain/entities/leave-batch-review";
import {
  canRunLeaveBatch,
  leaveBatchAccessLost,
  leaveBatchCommandRejected,
  leaveBatchReviewChanged,
} from "../../domain/policies/leave-batch-policy";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import {
  initialLeaveBatchStartState,
  type LeaveBatchStartState,
} from "../models/leave-batch-state";

type Submission = Readonly<{ operation: OperationId; input: LeaveBatchCreation }>;
/** Owns one reviewed employee selection and its original command until confirmation or disposal. */
export class LeaveBatchStartController {
  #state = initialLeaveBatchStartState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<LeaveUseCases, "reviewBatch" | "startBatch">,
    private readonly access: CompanyAccess,
    private readonly kind: LeaveBatchKind,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): LeaveBatchStartState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    if (this.#active) return;
    this.#active = true;
    this.refresh();
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.#submission = null;
    this.publish(initialLeaveBatchStartState);
  };
  refresh = (): void => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    this.#pending = null;
    this.publish(
      canRunLeaveBatch(this.access.permissions, this.kind)
        ? initialLeaveBatchStartState
        : {
            ...initialLeaveBatchStartState,
            stage: "unavailable",
            failure: { code: "access_denied", fields: {}, parameters: {} },
          },
    );
  };
  prepare = async (selection: LeaveBatchSelection): Promise<void> => {
    if (
      !this.#active ||
      this.#state.stage !== "editing" ||
      this.#submission ||
      selection.kind !== this.kind
    )
      return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...this.#state, stage: "preparing", review: null, failure: null });
    try {
      const result = await this.actions.reviewBatch.execute(this.access, selection, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...this.#state, stage: "reviewing", review: result.value }
          : {
              ...this.#state,
              stage: leaveBatchAccessLost(result.failure) ? "unavailable" : "editing",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...this.#state,
          stage: "editing",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  revise = (): void => {
    if (this.#active && !this.#submission && ["reviewing", "conflict"].includes(this.#state.stage))
      this.publish({ ...this.#state, stage: "editing", review: null, failure: null });
  };
  save = async (reason: string): Promise<void> => {
    const review = this.#state.review;
    if (
      !this.#active ||
      this.#state.stage !== "reviewing" ||
      !review ||
      !canRunLeaveBatch(this.access.permissions, review.kind)
    )
      return;
    try {
      this.#submission = Object.freeze({
        operation: this.nextIdentifier() as OperationId,
        input: Object.freeze({
          id: this.nextIdentifier() as LeaveBatchId,
          companyId: this.access.companyId,
          kind: review.kind,
          typeId: review.type.id,
          period: review.period,
          expectedPolicyVersion: review.type.version,
          employeeIds: Object.freeze([...review.employeeIds]),
          reason: reason.trim(),
        }),
      });
    } catch {
      this.publish({
        ...this.#state,
        failure: { code: "unexpected_error", fields: {}, parameters: {} },
      });
      return;
    }
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
        result = await this.actions.startBatch.execute(
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
          ...initialLeaveBatchStartState,
          stage: "saved",
          receipt: result.value,
          batchId: submission.input.id,
        });
      } else {
        const review = leaveBatchAccessLost(result.failure) ? null : this.#state.review;
        if (!wasUnconfirmed && leaveBatchCommandRejected(result.failure)) {
          this.#submission = null;
          this.publish({
            ...this.#state,
            review,
            failure: result.failure,
            operationId: null,
            stage:
              review === null
                ? "unavailable"
                : leaveBatchReviewChanged(result.failure)
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
  private publish(state: LeaveBatchStartState): void {
    this.#state = state;
    for (const listener of [...this.#listeners]) listener();
  }
}
