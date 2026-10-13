import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { type Failure, failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseDecision } from "../../domain/entities/expense-decision";
import {
  type ExpenseDecisionSnapshot,
  expenseDecisionSnapshot,
} from "../../domain/entities/expense-decision-snapshot";
import type { ExpenseSubmission } from "../../domain/entities/expense-submission";
import {
  expenseDecisionReviewChanged,
  expenseDecisionWasRejected,
} from "../../domain/policies/expense-decision-policy";
import { expenseEvidenceUnavailable } from "../../domain/policies/expense-read-failure-policy";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import {
  type ExpenseDecisionState,
  initialExpenseDecisionState,
} from "../models/expense-decision-state";

type Command = Readonly<{
  operation: OperationId;
  snapshot: ExpenseDecisionSnapshot;
  reason: string;
  acknowledgeDuplicates: boolean;
}>;

export class ExpenseDecisionController {
  #state = initialExpenseDecisionState;
  #active = false;
  #pending: AbortController | null = null;
  #command: Command | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<ExpenseUseCases, "reviewDecision" | "decideSubmission">,
    private readonly access: CompanyAccess,
    private readonly id: string,
    readonly decision: ExpenseDecision,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): ExpenseDecisionState => this.#state;
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
    this.#command = null;
    this.publish(initialExpenseDecisionState);
  };
  revokeEvidence = (observed: ExpenseSubmission, failure: Failure): void => {
    if (!this.#active || this.#state.review !== observed) return;
    if (this.#command) {
      this.publish({ ...this.#state, review: null });
      return;
    }
    this.#pending?.abort();
    this.#pending = null;
    this.publish({ ...initialExpenseDecisionState, stage: "unavailable", failure });
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#command) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialExpenseDecisionState);
    try {
      const result = await this.actions.reviewDecision.execute(
        this.access,
        this.id,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialExpenseDecisionState, stage: "reviewing", review: result.value }
          : { ...initialExpenseDecisionState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialExpenseDecisionState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  confirm = async (reason: string, acknowledgeDuplicates: boolean): Promise<void> => {
    const review = this.#state.review;
    if (!this.#active || this.#state.stage !== "reviewing" || !review) return;
    this.#command = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      snapshot: expenseDecisionSnapshot(review),
      reason,
      acknowledgeDuplicates,
    });
    await this.send(false);
  };
  selectLine = (id: string | null): void => {
    if (!this.#active || (id !== null && !this.#state.review?.lines.some((line) => line.id === id)))
      return;
    this.publish({ ...this.#state, selectedLineId: id });
  };
  retry = async (): Promise<void> => {
    if (this.#active && this.#state.stage === "unconfirmed") await this.send(true);
  };
  private async send(wasUnconfirmed: boolean): Promise<void> {
    const command = this.#command;
    if (!command) return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({
      ...this.#state,
      stage: "submitting",
      failure: null,
      operationId: command.operation,
    });
    try {
      let result: Result<MutationReceipt>;
      try {
        result = await this.actions.decideSubmission.execute(
          this.access,
          command.operation,
          command.snapshot,
          this.decision,
          command.reason,
          command.acknowledgeDuplicates,
          pending.signal,
        );
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#command = null;
        this.publish({ ...initialExpenseDecisionState, stage: "saved", receipt: result.value });
        return;
      }
      const review =
        expenseEvidenceUnavailable(result.failure) ||
        ["not_assigned_approver", "self_approval_denied"].includes(result.failure.code)
          ? null
          : this.#state.review;
      if (!wasUnconfirmed && expenseDecisionWasRejected(result.failure)) {
        this.#command = null;
        this.publish({
          ...this.#state,
          review,
          failure: result.failure,
          operationId: null,
          stage: !review
            ? "unavailable"
            : expenseDecisionReviewChanged(result.failure)
              ? "conflict"
              : "reviewing",
        });
      } else
        this.publish({ ...this.#state, review, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: ExpenseDecisionState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
