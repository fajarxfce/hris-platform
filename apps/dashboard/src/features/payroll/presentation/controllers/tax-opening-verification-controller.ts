import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { type Failure, failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { TaxOpeningVerification } from "../../domain/entities/tax-opening-change";
import {
  taxOpeningAccessLost,
  taxOpeningCommandWasRejected,
  taxOpeningNeedsReview,
} from "../../domain/policies/tax-opening-access-policy";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialTaxOpeningCommandState,
  type TaxOpeningCommandState,
} from "../models/tax-opening-command-state";

type Submission = Readonly<{ operation: OperationId; input: TaxOpeningVerification }>;
export class TaxOpeningVerificationController {
  #state = initialTaxOpeningCommandState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<PayrollUseCases, "reviewTaxOpeningAction" | "verifyTaxOpening">,
    private readonly access: CompanyAccess,
    private readonly employee: string,
    private readonly year: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): TaxOpeningCommandState => this.#state;
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
    this.publish(initialTaxOpeningCommandState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialTaxOpeningCommandState);
    try {
      const result = await this.actions.reviewTaxOpeningAction.execute(
        this.access,
        this.employee,
        this.year,
        "VERIFY",
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialTaxOpeningCommandState, stage: "editing", context: result.value }
          : { ...initialTaxOpeningCommandState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialTaxOpeningCommandState,
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
        result = await this.actions.verifyTaxOpening.execute(
          this.access,
          this.employee,
          this.year,
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
        this.publish({ ...initialTaxOpeningCommandState, stage: "saved", receipt: result.value });
      } else {
        const context =
          taxOpeningAccessLost(result.failure) ||
          ["self_payroll_input_review_denied", "payroll_tax_opening_author_cannot_verify"].includes(
            result.failure.code,
          )
            ? null
            : this.#state.context;
        if (!wasUnconfirmed && taxOpeningCommandWasRejected(result.failure)) {
          this.#submission = null;
          this.publish({
            ...this.#state,
            context,
            failure: result.failure,
            operationId: null,
            stage:
              context === null
                ? "unavailable"
                : taxOpeningNeedsReview(result.failure)
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
  private publish(state: TaxOpeningCommandState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
