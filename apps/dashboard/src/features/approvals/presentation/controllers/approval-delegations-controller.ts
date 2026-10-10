import type { AccountId } from "../../../../core/domain/identifiers";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import {
  type ApprovalDelegationsState,
  initialApprovalDelegationsState,
} from "../models/approval-delegations-state";

export class ApprovalDelegationsController {
  #state = initialApprovalDelegationsState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: ApprovalsUseCases["loadDelegations"],
    private readonly access: CompanyAccess,
    private readonly account: AccountId,
    private readonly after: string | null,
  ) {}
  getSnapshot = (): ApprovalDelegationsState => this.#state;
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
    this.publish(initialApprovalDelegationsState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialApprovalDelegationsState);
    try {
      const result = await this.load.execute(this.access, this.account, this.after, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", page: result.value, failure: null }
          : { stage: "unavailable", page: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          page: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: ApprovalDelegationsState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
