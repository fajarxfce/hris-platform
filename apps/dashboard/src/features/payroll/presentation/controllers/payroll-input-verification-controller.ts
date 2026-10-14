import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { type Failure, failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollInputVerification } from "../../domain/entities/payroll-input-change";
import {
  payrollInputAccessLost,
  payrollInputCommandWasRejected,
  payrollInputNeedsReview,
} from "../../domain/policies/payroll-input-access-policy";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialPayrollInputCommandState,
  type PayrollInputCommandState,
} from "../models/payroll-input-command-state";

type Submission = Readonly<{ operation: OperationId; input: PayrollInputVerification }>;
export class PayrollInputVerificationController {
  #state = initialPayrollInputCommandState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<PayrollUseCases, "reviewInputAction" | "verifyInput">,
    private readonly access: CompanyAccess,
    private readonly employee: string,
    private readonly month: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): PayrollInputCommandState => this.#state;
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
    this.publish(initialPayrollInputCommandState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialPayrollInputCommandState);
    try {
      const result = await this.actions.reviewInputAction.execute(
        this.access,
        this.employee,
        this.month,
        "VERIFY",
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialPayrollInputCommandState, stage: "editing", context: result.value }
          : { ...initialPayrollInputCommandState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialPayrollInputCommandState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  verify = async (reason: string): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing" || !this.#state.context?.current) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        expectedVersion: this.#state.context.current.version,
        reason: reason.trim(),
      }),
    });
    await this.submit(false);
  };
  reportValidationFailure = (failure: Failure): void => {
    if (this.#active && this.#state.stage === "editing") this.publish({ ...this.#state, failure });
  };
  retryVerification = async (): Promise<void> => {
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
        result = await this.actions.verifyInput.execute(
          this.access,
          this.employee,
          this.month,
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
        this.publish({ ...initialPayrollInputCommandState, stage: "saved", receipt: result.value });
      } else {
        const context =
          payrollInputAccessLost(result.failure) ||
          ["self_payroll_input_review_denied", "payroll_input_author_cannot_verify"].includes(
            result.failure.code,
          )
            ? null
            : this.#state.context;
        if (!wasUnconfirmed && payrollInputCommandWasRejected(result.failure)) {
          this.#submission = null;
          this.publish({
            ...this.#state,
            context,
            failure: result.failure,
            operationId: null,
            stage:
              context === null
                ? "unavailable"
                : payrollInputNeedsReview(result.failure)
                  ? "conflict"
                  : "editing",
          });
        } else
          this.publish({ ...this.#state, context, stage: "unconfirmed", failure: result.failure });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: PayrollInputCommandState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
