import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveBatchResume } from "../../domain/entities/leave-batch-command";
import type { LeaveBatchDetails } from "../../domain/entities/leave-batch-details";
import {
  canResumeLeaveBatch,
  leaveBatchAccessLost,
  leaveBatchCommandRejected,
  leaveBatchReviewChanged,
} from "../../domain/policies/leave-batch-policy";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import { initialLeaveBatchCommand, type LeaveBatchCommandState } from "../models/leave-batch-state";

type Submission = Readonly<{ operation: OperationId; input: LeaveBatchResume }>;
export class LeaveBatchResumeController {
  #state = initialLeaveBatchCommand<LeaveBatchDetails>();
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LeaveUseCases["loadBatch"],
    private readonly command: LeaveUseCases["resumeBatch"],
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): LeaveBatchCommandState<LeaveBatchDetails> => this.#state;
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
    this.publish(initialLeaveBatchCommand());
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialLeaveBatchCommand());
    try {
      const result = await this.load.execute(this.access, this.id, null, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...this.#state, stage: "reviewing", review: result.value }
          : { ...this.#state, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...this.#state,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (reason: string): Promise<void> => {
    const review = this.#state.review;
    if (
      !this.#active ||
      this.#state.stage !== "reviewing" ||
      !review ||
      !canResumeLeaveBatch(this.access, review)
    )
      return;
    try {
      this.#submission = Object.freeze({
        operation: this.nextIdentifier() as OperationId,
        input: Object.freeze({
          id: review.batch.id,
          companyId: this.access.companyId,
          kind: review.batch.kind,
          expectedVersion: review.batch.version,
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
        result = await this.command.execute(
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
          ...initialLeaveBatchCommand<LeaveBatchDetails>(),
          stage: "saved",
          receipt: result.value,
          batchId: submission.input.id,
        });
      } else {
        const review =
          leaveBatchAccessLost(result.failure) || result.failure.code === "leave_batch_not_found"
            ? null
            : this.#state.review;
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
  private publish(state: LeaveBatchCommandState<LeaveBatchDetails>): void {
    this.#state = state;
    for (const listener of [...this.#listeners]) listener();
  }
}
