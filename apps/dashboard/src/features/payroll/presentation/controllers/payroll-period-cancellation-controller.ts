import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  payrollPeriodAccessLost,
  payrollPeriodCommandRejected,
} from "../../domain/policies/payroll-period-policy";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialPayrollPeriodCancellationState,
  type PayrollPeriodCancellationState,
} from "../models/payroll-period-cancellation-state";

type Submission = Readonly<{ operation: OperationId; version: number; reason: string }>;
export class PayrollPeriodCancellationController {
  #state = initialPayrollPeriodCancellationState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<PayrollUseCases, "reviewPeriodCancellation" | "cancelPeriod">,
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): PayrollPeriodCancellationState => this.#state;
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
    this.publish(initialPayrollPeriodCancellationState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialPayrollPeriodCancellationState);
    try {
      const result = await this.actions.reviewPeriodCancellation.execute(
        this.access,
        this.id,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialPayrollPeriodCancellationState, stage: "reviewing", review: result.value }
          : {
              ...initialPayrollPeriodCancellationState,
              stage: "unavailable",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialPayrollPeriodCancellationState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (reason: string): Promise<void> => {
    if (!this.#active || this.#state.stage !== "reviewing" || !this.#state.review) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      version: this.#state.review.period.version,
      reason,
    });
    await this.submit(false);
  };
  retrySave = async (): Promise<void> => {
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
        result = await this.actions.cancelPeriod.execute(
          this.access,
          this.id,
          submission.operation,
          submission.version,
          submission.reason,
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
          ...initialPayrollPeriodCancellationState,
          stage: "saved",
          receipt: result.value,
        });
      } else {
        const review = payrollPeriodAccessLost(result.failure) ? null : this.#state.review;
        if (!wasUnconfirmed && payrollPeriodCommandRejected(result.failure)) {
          this.#submission = null;
          this.publish({
            ...this.#state,
            review,
            failure: result.failure,
            operationId: null,
            stage:
              review === null
                ? "unavailable"
                : [
                      "stale_version",
                      "payroll_period_not_draft",
                      "payroll_period_revision_limit",
                    ].includes(result.failure.code)
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
  private publish(state: PayrollPeriodCancellationState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
