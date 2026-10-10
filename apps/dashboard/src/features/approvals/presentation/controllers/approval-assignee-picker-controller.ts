import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalKind } from "../../domain/entities/approval-request";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import {
  type ApprovalAssigneePickerState,
  initialApprovalAssigneePickerState,
} from "../models/approval-assignee-picker-state";

export class ApprovalAssigneePickerController {
  #state = initialApprovalAssigneePickerState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: ApprovalsUseCases["loadAssignees"],
    private readonly access: CompanyAccess,
    private readonly kind: ApprovalKind,
  ) {}
  getSnapshot = (): ApprovalAssigneePickerState => this.#state;
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
    this.publish(initialApprovalAssigneePickerState);
  };
  search = async (query: string): Promise<void> => {
    if (!this.#active) return;
    this.publish({ ...this.#state, query, after: null });
    await this.refresh();
  };
  firstPage = async (): Promise<void> => {
    if (!this.#active) return;
    this.publish({ ...this.#state, after: null });
    await this.refresh();
  };
  nextPage = async (): Promise<void> => {
    const after = this.#state.page?.nextCursor;
    if (!this.#active || this.#state.stage !== "ready" || !after) return;
    this.publish({ ...this.#state, after });
    await this.refresh();
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    const search = { kind: this.kind, query: this.#state.query, after: this.#state.after };
    this.publish({ ...this.#state, stage: "loading", page: null, failure: null });
    try {
      const result = await this.load.execute(this.access, search, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...this.#state, stage: "ready", page: result.value, failure: null }
          : { ...this.#state, stage: "unavailable", page: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...this.#state,
          stage: "unavailable",
          page: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: ApprovalAssigneePickerState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
