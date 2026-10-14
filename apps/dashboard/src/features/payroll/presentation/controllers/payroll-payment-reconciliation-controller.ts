import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentOutcome } from "../../domain/entities/payroll-payment-outcome";
import {
  type PayrollPaymentReconciliationSnapshot,
  payrollPaymentReconciliationSnapshot,
} from "../../domain/entities/payroll-payment-reconciliation-snapshot";
import { payrollPaymentEvidenceUnavailable } from "../../domain/policies/payroll-payment-failure-policy";
import {
  payrollPaymentReconciliationReviewChanged,
  payrollPaymentReconciliationWasRejected,
  preparePayrollPaymentOutcome,
} from "../../domain/policies/payroll-payment-reconciliation-policy";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialPayrollPaymentReconciliationState,
  type PayrollPaymentReconciliationState,
} from "../models/payroll-payment-reconciliation-state";

type Command = Readonly<{
  operation: OperationId;
  snapshot: PayrollPaymentReconciliationSnapshot;
  reason: string;
  results: readonly PayrollPaymentOutcome[];
}>;

export class PayrollPaymentReconciliationController {
  #state = initialPayrollPaymentReconciliationState;
  #active = false;
  #pending: AbortController | null = null;
  #command: Command | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<PayrollUseCases, "reviewReconciliation" | "reconcilePayment">,
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): PayrollPaymentReconciliationState => this.#state;
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
    this.publish(initialPayrollPaymentReconciliationState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#command) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialPayrollPaymentReconciliationState);
    try {
      const result = await this.actions.reviewReconciliation.execute(
        this.access,
        this.id,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? {
              ...initialPayrollPaymentReconciliationState,
              stage: "reviewing",
              review: result.value,
            }
          : {
              ...initialPayrollPaymentReconciliationState,
              stage: "unavailable",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialPayrollPaymentReconciliationState,
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
      snapshot: payrollPaymentReconciliationSnapshot(review),
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
  saveOutcome = (input: PayrollPaymentOutcome): void => {
    if (
      !this.#active ||
      this.#state.stage !== "reviewing" ||
      !this.#state.review ||
      this.#state.selectedItemId !== input.itemId
    )
      return;
    const result = preparePayrollPaymentOutcome(
      payrollPaymentReconciliationSnapshot(this.#state.review),
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
          ...initialPayrollPaymentReconciliationState,
          stage: "saved",
          receipt: result.value,
        });
        return;
      }
      const review =
        payrollPaymentEvidenceUnavailable(result.failure) ||
        ["independent_payment_release_required"].includes(result.failure.code)
          ? null
          : this.#state.review;
      if (!wasUnconfirmed && payrollPaymentReconciliationWasRejected(result.failure)) {
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
            : payrollPaymentReconciliationReviewChanged(result.failure)
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
  private publish(state: PayrollPaymentReconciliationState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
