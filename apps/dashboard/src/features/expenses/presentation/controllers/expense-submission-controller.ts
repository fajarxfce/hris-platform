import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import {
  type ExpenseSubmissionState,
  initialExpenseReceiptDownload,
  initialExpenseSubmissionState,
} from "../models/expense-submission-state";
export class ExpenseSubmissionController {
  #active = false;
  #read: AbortController | null = null;
  #download: AbortController | null = null;
  #state = initialExpenseSubmissionState;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: ExpenseUseCases["loadSubmission"],
    private readonly receipt: ExpenseUseCases["downloadReceipt"],
    private readonly access: CompanyAccess,
    private readonly id: string,
  ) {}
  getSnapshot = (): ExpenseSubmissionState => this.#state;
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
    this.#read?.abort();
    this.#read = null;
    this.#download?.abort();
    this.#download = null;
    this.publish(initialExpenseSubmissionState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#read?.abort();
    this.#download?.abort();
    this.#download = null;
    const pending = new AbortController();
    this.#read = pending;
    this.publish(initialExpenseSubmissionState);
    try {
      const result = await this.load.execute(this.access, this.id, pending.signal);
      if (pending.signal.aborted || this.#read !== pending) return;
      this.publish(
        result.ok
          ? { ...initialExpenseSubmissionState, stage: "ready", submission: result.value }
          : { ...initialExpenseSubmissionState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#read === pending)
        this.publish({
          ...initialExpenseSubmissionState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#read === pending) this.#read = null;
    }
  };
  cancelDownload = (): void => {
    this.#download?.abort();
    this.#download = null;
    this.publish({ ...this.#state, download: initialExpenseReceiptDownload });
  };
  downloadReceipt = async (revisionId: string): Promise<void> => {
    const submission = this.#state.submission;
    if (
      !this.#active ||
      this.#state.stage !== "ready" ||
      !submission ||
      this.#download ||
      !submission.lines.some((line) =>
        line.receipts.some((receipt) => receipt.revisionId === revisionId),
      )
    )
      return;
    const pending = new AbortController();
    this.#download = pending;
    this.publish({ ...this.#state, download: { stage: "downloading", revisionId, failure: null } });
    try {
      const result = await this.receipt.execute(
        this.access,
        submission,
        revisionId,
        pending.signal,
      );
      if (
        pending.signal.aborted ||
        this.#download !== pending ||
        this.#state.submission !== submission
      )
        return;
      if (
        !result.ok &&
        [
          "access_denied",
          "company_access_denied",
          "expense_receipt_not_found",
          "expense_submission_not_found",
          "authentication_required",
          "session_revoked",
          "unauthenticated",
          "mfa_required",
          "mfa_setup_required",
          "recent_authentication_required",
          "company_module_disabled",
          "company_maintenance",
          "client_update_required",
        ].includes(result.failure.code)
      ) {
        this.publish({
          ...initialExpenseSubmissionState,
          stage: "unavailable",
          failure: result.failure,
        });
      } else
        this.publish({
          ...this.#state,
          download: result.ok
            ? { stage: "started", revisionId, failure: null }
            : { stage: "failed", revisionId, failure: result.failure },
        });
    } catch {
      if (
        !pending.signal.aborted &&
        this.#download === pending &&
        this.#state.submission === submission
      )
        this.publish({
          ...this.#state,
          download: {
            stage: "failed",
            revisionId,
            failure: { code: "unexpected_error", fields: {}, parameters: {} },
          },
        });
    } finally {
      if (this.#download === pending) this.#download = null;
    }
  };
  private publish(state: ExpenseSubmissionState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
