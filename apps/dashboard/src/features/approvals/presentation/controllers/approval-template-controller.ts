import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import {
  type ApprovalTemplateState,
  initialApprovalTemplateState,
} from "../models/approval-template-state";

export class ApprovalTemplateController {
  #state = initialApprovalTemplateState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: ApprovalsUseCases["loadTemplate"],
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly revision: string | null,
  ) {}
  getSnapshot = (): ApprovalTemplateState => this.#state;
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
    this.publish(initialApprovalTemplateState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialApprovalTemplateState);
    try {
      const result = await this.load.execute(this.access, this.id, this.revision, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", template: result.value, failure: null }
          : { stage: "unavailable", template: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          template: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: ApprovalTemplateState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
