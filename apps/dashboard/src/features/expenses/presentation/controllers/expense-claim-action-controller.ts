import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseClaimAction } from "../../domain/entities/expense-claim-action";
import {
  type ExpenseClaimActionSnapshot,
  expenseClaimActionSnapshot,
} from "../../domain/entities/expense-claim-action-snapshot";
import {
  expenseClaimActionWasRejected,
  expenseClaimReviewChanged,
} from "../../domain/policies/expense-claim-action-policy";
import { expenseDraftAccessLost } from "../../domain/policies/expense-draft-policy";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import {
  type ExpenseClaimActionState,
  initialExpenseClaimActionState,
} from "../models/expense-claim-action-state";

type Command = Readonly<{
  operation: OperationId;
  snapshot: ExpenseClaimActionSnapshot;
  reason: string;
  submissionId: string | null;
}>;
export class ExpenseClaimActionController {
  #state = initialExpenseClaimActionState;
  #active = false;
  #pending: AbortController | null = null;
  #command: Command | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<ExpenseUseCases, "reviewClaimAction" | "transitionClaim">,
    private readonly access: CompanyAccess,
    private readonly id: string,
    readonly action: ExpenseClaimAction,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): ExpenseClaimActionState => this.#state;
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
    this.publish(initialExpenseClaimActionState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#command) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialExpenseClaimActionState);
    try {
      const result = await this.actions.reviewClaimAction.execute(
        this.access,
        this.id,
        this.action,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialExpenseClaimActionState, stage: "reviewing", review: result.value }
          : { ...initialExpenseClaimActionState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialExpenseClaimActionState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  selectLine = (id: string | null): void => {
    if (
      !this.#active ||
      (id !== null && !this.#state.review?.claim.draft.lines.some((line) => line.id === id))
    )
      return;
    this.publish({ ...this.#state, selectedLineId: id });
  };
  confirm = async (reason: string): Promise<void> => {
    if (!this.#active || this.#state.stage !== "reviewing" || !this.#state.review) return;
    this.#command = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      snapshot: expenseClaimActionSnapshot(this.#state.review.claim),
      reason,
      submissionId: this.action === "SUBMIT" ? this.nextIdentifier() : null,
    });
    await this.send(false);
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
      selectedLineId: null,
    });
    try {
      let result: Result<MutationReceipt>;
      try {
        result = await this.actions.transitionClaim.execute(
          this.access,
          command.operation,
          command.snapshot,
          this.action,
          command.reason,
          command.submissionId,
          pending.signal,
        );
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#command = null;
        this.publish({ ...initialExpenseClaimActionState, stage: "saved", receipt: result.value });
        return;
      }
      const review = expenseDraftAccessLost(result.failure) ? null : this.#state.review;
      if (!wasUnconfirmed && expenseClaimActionWasRejected(result.failure)) {
        this.#command = null;
        this.publish({
          ...this.#state,
          review,
          failure: result.failure,
          operationId: null,
          stage: !review
            ? "unavailable"
            : expenseClaimReviewChanged(result.failure)
              ? "conflict"
              : "reviewing",
        });
      } else
        this.publish({ ...this.#state, review, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: ExpenseClaimActionState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
