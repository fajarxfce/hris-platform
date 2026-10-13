import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseSubmission } from "../../domain/entities/expense-submission";
import { expenseEvidenceUnavailable } from "../../domain/policies/expense-read-failure-policy";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import {
  type ExpenseReceiptDownloadState,
  initialExpenseReceiptDownload,
} from "../models/expense-receipt-state";

export class ExpenseReceiptController {
  #active = false;
  #pending: AbortController | null = null;
  #state = initialExpenseReceiptDownload;
  #listeners = new Set<() => void>();
  constructor(
    private readonly download: ExpenseUseCases["downloadReceipt"],
    private readonly access: CompanyAccess,
    private readonly submission: ExpenseSubmission,
  ) {}
  getSnapshot = (): ExpenseReceiptDownloadState => this.#state;
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
    this.publish(initialExpenseReceiptDownload);
  };
  open = async (revisionId: string): Promise<void> => {
    if (
      !this.#active ||
      this.#pending ||
      this.#state.stage === "unavailable" ||
      !this.submission.lines.some((line) =>
        line.receipts.some((receipt) => receipt.revisionId === revisionId),
      )
    )
      return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "downloading", revisionId, failure: null });
    try {
      const result = await this.download.execute(
        this.access,
        this.submission,
        revisionId,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "started", revisionId, failure: null }
          : {
              stage: expenseEvidenceUnavailable(result.failure) ? "unavailable" : "failed",
              revisionId,
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "failed",
          revisionId,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: ExpenseReceiptDownloadState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
