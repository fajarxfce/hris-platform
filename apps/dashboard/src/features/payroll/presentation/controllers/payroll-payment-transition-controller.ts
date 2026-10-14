import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  type PayrollPaymentSnapshot,
  payrollPaymentSnapshot,
} from "../../domain/entities/payroll-payment-snapshot";
import type { PayrollPaymentTransition } from "../../domain/entities/payroll-payment-transition";
import { payrollPaymentEvidenceUnavailable } from "../../domain/policies/payroll-payment-failure-policy";
import {
  payrollPaymentActionWasRejected,
  payrollPaymentReviewChanged,
} from "../../domain/policies/payroll-payment-transition-policy";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialPayrollPaymentTransitionState,
  type PayrollPaymentTransitionState,
} from "../models/payroll-payment-transition-state";

type Command = Readonly<{
  operation: OperationId;
  snapshot: PayrollPaymentSnapshot;
  reason: string;
}>;

export class PayrollPaymentTransitionController {
  #state = initialPayrollPaymentTransitionState;
  #active = false;
  #pending: AbortController | null = null;
  #command: Command | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      PayrollUseCases,
      "reviewPaymentTransition" | "transitionPayment"
    >,
    private readonly access: CompanyAccess,
    private readonly id: string,
    readonly transition: PayrollPaymentTransition,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): PayrollPaymentTransitionState => this.#state;
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
    this.publish(initialPayrollPaymentTransitionState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#command) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialPayrollPaymentTransitionState);
    try {
      const result = await this.actions.reviewPaymentTransition.execute(
        this.access,
        this.id,
        this.transition,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialPayrollPaymentTransitionState, stage: "reviewing", review: result.value }
          : {
              ...initialPayrollPaymentTransitionState,
              stage: "unavailable",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialPayrollPaymentTransitionState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  confirm = async (reason: string): Promise<void> => {
    const review = this.#state.review;
    if (!this.#active || this.#state.stage !== "reviewing" || !review) return;
    this.#command = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      snapshot: payrollPaymentSnapshot(review),
      reason,
    });
    await this.send(false);
  };
  selectItem = (id: string | null): void => {
    if (
      !this.#active ||
      (id !== null && !this.#state.review?.batch.items.some((line) => line.id === id))
    )
      return;
    this.publish({ ...this.#state, selectedItemId: id });
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
        result = await this.actions.transitionPayment.execute(
          this.access,
          command.operation,
          command.snapshot,
          this.transition,
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
          ...initialPayrollPaymentTransitionState,
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
      if (!wasUnconfirmed && payrollPaymentActionWasRejected(result.failure)) {
        this.#command = null;
        this.publish({
          ...this.#state,
          review,
          failure: result.failure,
          operationId: null,
          stage: !review
            ? "unavailable"
            : payrollPaymentReviewChanged(result.failure)
              ? "conflict"
              : "reviewing",
        });
      } else
        this.publish({ ...this.#state, review, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: PayrollPaymentTransitionState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
