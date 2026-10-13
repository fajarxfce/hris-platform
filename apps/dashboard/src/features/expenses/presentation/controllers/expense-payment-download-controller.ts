import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePaymentReview } from "../../domain/entities/expense-payment-review";
import { expenseEvidenceUnavailable } from "../../domain/policies/expense-read-failure-policy";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import {
  type ExpensePaymentDownloadState,
  initialExpensePaymentDownload,
} from "../models/expense-payment-download-state";
export class ExpensePaymentDownloadController {
  #active = false;
  #pending: AbortController | null = null;
  #state = initialExpensePaymentDownload;
  #listeners = new Set<() => void>();
  constructor(
    private readonly download: ExpenseUseCases["downloadPayment"],
    private readonly access: CompanyAccess,
    private readonly review: ExpensePaymentReview,
  ) {}
  getSnapshot = (): ExpensePaymentDownloadState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    this.#active = true;
  };
  deactivate = (): void => {
    this.#active = false;
    this.cancel();
  };
  cancel = (): void => {
    this.#pending?.abort();
    this.#pending = null;
    this.publish(initialExpensePaymentDownload);
  };
  open = async (): Promise<void> => {
    if (
      !this.#active ||
      this.#pending ||
      this.#state.stage === "unavailable" ||
      !this.review.availableActions.includes("EXPORT")
    )
      return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "downloading", failure: null });
    try {
      const result = await this.download.execute(this.access, this.review, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "started", failure: null }
          : {
              stage:
                expenseEvidenceUnavailable(result.failure) ||
                [
                  "expense_payment_batch_not_found",
                  "expense_payment_instructions_unavailable",
                ].includes(result.failure.code)
                  ? "unavailable"
                  : "failed",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "failed",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: ExpensePaymentDownloadState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
