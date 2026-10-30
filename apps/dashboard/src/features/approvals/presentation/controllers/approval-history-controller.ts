import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import {
  type ApprovalHistoryState,
  initialApprovalHistoryState,
} from "../models/approval-history-state";
export class ApprovalHistoryController {
  #state = initialApprovalHistoryState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: ApprovalsUseCases["loadHistory"],
    private readonly access: CompanyAccess,
    private readonly id: string,
  ) {}
  getSnapshot = (): ApprovalHistoryState => this.#state;
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
    this.publish(initialApprovalHistoryState);
  };
  refresh = async (): Promise<void> => this.read(null, null);
  first = async (): Promise<void> => {
    if (this.#state.stage !== "ready" || !this.#state.history) return;
    await this.read(null, this.#state.history.request.version);
  };
  next = async (): Promise<void> => {
    if (this.#state.stage !== "ready" || !this.#state.history?.reassignments.nextCursor) return;
    await this.read(
      Number(this.#state.history.reassignments.nextCursor),
      this.#state.history.request.version,
    );
  };
  private async read(after: number | null, expectedVersion: number | null): Promise<void> {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...initialApprovalHistoryState, after });
    try {
      const result = await this.load.execute(
        this.access,
        this.id,
        after,
        expectedVersion,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", history: result.value, after, failure: null }
          : { stage: "unavailable", history: null, after, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          history: null,
          after,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: ApprovalHistoryState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
