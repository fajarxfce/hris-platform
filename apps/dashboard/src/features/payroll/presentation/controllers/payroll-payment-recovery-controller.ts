import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPaymentRecoveryInput } from "../../domain/entities/payroll-payment-recovery-input";
import {
  type PayrollPaymentRecoverySnapshot,
  payrollPaymentRecoverySnapshot,
} from "../../domain/entities/payroll-payment-recovery-snapshot";
import {
  payrollPaymentRecoveryEvidenceUnavailable,
  payrollPaymentRecoveryReviewChanged,
  payrollPaymentRecoveryWasRejected,
} from "../../domain/policies/payroll-payment-recovery-policy";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialPayrollPaymentRecoveryState,
  type PayrollPaymentRecoveryState,
} from "../models/payroll-payment-recovery-state";

type Submission = Readonly<{
  operation: OperationId;
  id: string;
  snapshot: PayrollPaymentRecoverySnapshot;
  input: PayrollPaymentRecoveryInput;
}>;

export class PayrollPaymentRecoveryController {
  #state = initialPayrollPaymentRecoveryState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      PayrollUseCases,
      "reviewPaymentRecovery" | "confirmPaymentRecovery"
    >,
    private readonly access: CompanyAccess,
    private readonly assessment: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): PayrollPaymentRecoveryState => this.#state;
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
    this.publish(initialPayrollPaymentRecoveryState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialPayrollPaymentRecoveryState);
    try {
      const result = await this.actions.reviewPaymentRecovery.execute(
        this.access,
        this.assessment,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialPayrollPaymentRecoveryState, stage: "ready", review: result.value }
          : {
              ...initialPayrollPaymentRecoveryState,
              stage: "unavailable",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialPayrollPaymentRecoveryState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  confirm = async (input: PayrollPaymentRecoveryInput): Promise<void> => {
    const review = this.#state.review;
    if (!this.#active || this.#state.stage !== "ready" || !review?.canConfirm) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      id: this.nextIdentifier(),
      snapshot: payrollPaymentRecoverySnapshot(review),
      input: Object.freeze({ ...input }),
    });
    await this.send(false);
  };
  retry = async (): Promise<void> => {
    if (this.#active && this.#state.stage === "unconfirmed") await this.send(true);
  };
  private async send(wasUnconfirmed: boolean): Promise<void> {
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
        result = await this.actions.confirmPaymentRecovery.execute(
          this.access,
          submission.operation,
          submission.id,
          submission.snapshot,
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
          ...initialPayrollPaymentRecoveryState,
          stage: "saved",
          receipt: result.value,
        });
        return;
      }
      const review = payrollPaymentRecoveryEvidenceUnavailable(result.failure)
        ? null
        : this.#state.review;
      if (!wasUnconfirmed && payrollPaymentRecoveryWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          review,
          failure: result.failure,
          operationId: null,
          stage: !review
            ? "unavailable"
            : payrollPaymentRecoveryReviewChanged(result.failure)
              ? "conflict"
              : "ready",
        });
      } else
        this.publish({ ...this.#state, review, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: PayrollPaymentRecoveryState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
