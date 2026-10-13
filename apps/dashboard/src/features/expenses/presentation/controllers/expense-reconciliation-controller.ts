import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePaymentOutcome } from "../../domain/entities/expense-payment-outcome";
import {
  type ExpenseReconciliationSnapshot,
  expenseReconciliationSnapshot,
} from "../../domain/entities/expense-reconciliation-snapshot";
import { expenseEvidenceUnavailable } from "../../domain/policies/expense-read-failure-policy";
import {
  expenseReconciliationReviewChanged,
  expenseReconciliationWasRejected,
  prepareExpensePaymentOutcome,
} from "../../domain/policies/expense-reconciliation-policy";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import {
  type ExpenseReconciliationState,
  initialExpenseReconciliationState,
} from "../models/expense-reconciliation-state";

type Command = Readonly<{
  operation: OperationId;
  snapshot: ExpenseReconciliationSnapshot;
  reason: string;
  results: readonly ExpensePaymentOutcome[];
}>;

export class ExpenseReconciliationController {
  #state = initialExpenseReconciliationState;
  #active = false;
  #pending: AbortController | null = null;
  #command: Command | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<ExpenseUseCases, "reviewReconciliation" | "reconcilePayment">,
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): ExpenseReconciliationState => this.#state;
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
    this.publish(initialExpenseReconciliationState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#command) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialExpenseReconciliationState);
    try {
      const result = await this.actions.reviewReconciliation.execute(
        this.access,
        this.id,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialExpenseReconciliationState, stage: "reviewing", review: result.value }
          : {
              ...initialExpenseReconciliationState,
              stage: "unavailable",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialExpenseReconciliationState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  confirm = async (reason: string): Promise<void> => {
    const review = this.#state.review;
    if (
      !this.#active ||
      this.#state.stage !== "reviewing" ||
      !review ||
      this.#state.selectedItemId !== null ||
      this.#state.results.length === 0
    )
      return;
    this.#command = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      snapshot: expenseReconciliationSnapshot(review),
      reason,
      results: Object.freeze([...this.#state.results]),
    });
    await this.send(false);
  };
  selectItem = (id: string | null): void => {
    if (
      !this.#active ||
      this.#state.stage !== "reviewing" ||
      (id !== null &&
        !this.#state.review?.batch.items.some(
          (item) => item.id === id && item.status === "PENDING",
        ))
    )
      return;
    this.publish({ ...this.#state, selectedItemId: id, entryFailure: null, entryDirty: false });
  };
  setEntryDirty = (itemId: string, dirty: boolean): void => {
    if (
      !this.#active ||
      this.#state.stage !== "reviewing" ||
      this.#state.selectedItemId !== itemId ||
      this.#state.entryDirty === dirty
    )
      return;
    this.publish({ ...this.#state, entryDirty: dirty });
  };
  saveOutcome = (input: ExpensePaymentOutcome): void => {
    if (
      !this.#active ||
      this.#state.stage !== "reviewing" ||
      !this.#state.review ||
      this.#state.selectedItemId !== input.itemId
    )
      return;
    const result = prepareExpensePaymentOutcome(
      expenseReconciliationSnapshot(this.#state.review),
      input,
    );
    if (!result.ok) {
      this.publish({ ...this.#state, entryFailure: result.failure });
      return;
    }
    this.publish({
      ...this.#state,
      selectedItemId: null,
      entryFailure: null,
      entryDirty: false,
      failure: null,
      results: Object.freeze([
        ...this.#state.results.filter((item) => item.itemId !== input.itemId),
        result.value,
      ]),
    });
  };
  clearOutcome = (): void => {
    if (!this.#active || this.#state.stage !== "reviewing") return;
    this.publish({
      ...this.#state,
      selectedItemId: null,
      entryFailure: null,
      entryDirty: false,
      results: Object.freeze(
        this.#state.results.filter((item) => item.itemId !== this.#state.selectedItemId),
      ),
    });
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
        result = await this.actions.reconcilePayment.execute(
          this.access,
          command.operation,
          command.snapshot,
          command.results,
          command.reason,
          pending.signal,
        );
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#command = null;
        this.publish({
          ...initialExpenseReconciliationState,
          stage: "saved",
          receipt: result.value,
        });
        return;
      }
      const review =
        expenseEvidenceUnavailable(result.failure) ||
        ["independent_payment_release_required", "independent_expense_payment_required"].includes(
          result.failure.code,
        )
          ? null
          : this.#state.review;
      if (!wasUnconfirmed && expenseReconciliationWasRejected(result.failure)) {
        this.#command = null;
        this.publish({
          ...this.#state,
          review,
          results: review ? this.#state.results : [],
          selectedItemId: null,
          entryFailure: null,
          entryDirty: false,
          failure: result.failure,
          operationId: null,
          stage: !review
            ? "unavailable"
            : expenseReconciliationReviewChanged(result.failure)
              ? "conflict"
              : "reviewing",
        });
      } else
        this.publish({
          ...this.#state,
          review,
          results: review ? this.#state.results : [],
          selectedItemId: null,
          entryFailure: null,
          entryDirty: false,
          stage: "unconfirmed",
          failure: result.failure,
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: ExpenseReconciliationState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
