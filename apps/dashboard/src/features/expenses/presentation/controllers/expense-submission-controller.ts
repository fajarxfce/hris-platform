import type { Failure } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseSubmission } from "../../domain/entities/expense-submission";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import {
  type ExpenseSubmissionState,
  initialExpenseSubmissionState,
} from "../models/expense-submission-state";

export class ExpenseSubmissionController {
  #active = false;
  #read: AbortController | null = null;
  #state = initialExpenseSubmissionState;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: ExpenseUseCases["loadSubmission"],
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
    this.publish(initialExpenseSubmissionState);
  };
  revokeEvidence = (observed: ExpenseSubmission, failure: Failure): void => {
    if (!this.#active || this.#state.submission !== observed) return;
    this.#read?.abort();
    this.#read = null;
    this.publish({ ...initialExpenseSubmissionState, stage: "unavailable", failure });
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#read?.abort();
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
  private publish(state: ExpenseSubmissionState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
