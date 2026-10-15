import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentPublicationRecovery } from "../../domain/entities/payroll-amendment-publication-command";
import type { PayrollAmendmentPublicationDetails } from "../../domain/entities/payroll-amendment-publication-details";
import {
  amendmentPublicationAccessLost,
  amendmentPublicationChanged,
  amendmentPublicationCommandRejected,
} from "../../domain/policies/payroll-amendment-publication-policy";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialPayrollCommandState,
  type PayrollCommandState,
} from "../models/payroll-command-state";

type State = PayrollCommandState<PayrollAmendmentPublicationDetails>;
type Submission = Readonly<{ operation: OperationId; input: PayrollAmendmentPublicationRecovery }>;
export class PayrollAmendmentPublicationRecoveryController {
  #state = initialPayrollCommandState<PayrollAmendmentPublicationDetails>();
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      PayrollUseCases,
      "reviewAmendmentPublicationRecovery" | "recoverAmendmentPublication"
    >,
    private readonly access: CompanyAccess,
    private readonly amendmentId: string,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): State => this.#state;
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
    this.publish(initialPayrollCommandState<PayrollAmendmentPublicationDetails>());
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialPayrollCommandState<PayrollAmendmentPublicationDetails>());
    try {
      const result = await this.actions.reviewAmendmentPublicationRecovery.execute(
        this.access,
        this.amendmentId,
        this.id,
        pending.signal,
      );
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
      !review?.availableActions.includes("RECOVER")
    )
      return;
    try {
      this.#submission = Object.freeze({
        operation: this.nextIdentifier() as OperationId,
        input: Object.freeze({
          expectedVersion: review.amendment.version,
          expectedJobVersion: review.job.version,
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
        result = await this.actions.recoverAmendmentPublication.execute(
          this.access,
          this.amendmentId,
          this.id,
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
          ...initialPayrollCommandState<PayrollAmendmentPublicationDetails>(),
          stage: "saved",
          receipt: result.value,
        });
      } else {
        const review = amendmentPublicationAccessLost(result.failure) ? null : this.#state.review;
        if (!wasUnconfirmed && amendmentPublicationCommandRejected(result.failure)) {
          this.#submission = null;
          this.publish({
            ...this.#state,
            review,
            failure: result.failure,
            operationId: null,
            stage:
              review === null
                ? "unavailable"
                : amendmentPublicationChanged(result.failure)
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
  private publish(state: State): void {
    this.#state = state;
    for (const listener of [...this.#listeners]) listener();
  }
}
