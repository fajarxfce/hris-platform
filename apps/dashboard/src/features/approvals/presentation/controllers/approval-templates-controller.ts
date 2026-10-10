import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalTemplateSearch } from "../../domain/entities/approval-template";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import {
  type ApprovalTemplatesState,
  initialApprovalTemplatesState,
} from "../models/approval-templates-state";

export class ApprovalTemplatesController {
  #state = initialApprovalTemplatesState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: ApprovalsUseCases["loadTemplates"],
    private readonly access: CompanyAccess,
    private readonly search: ApprovalTemplateSearch,
  ) {}
  getSnapshot = (): ApprovalTemplatesState => this.#state;
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
    this.publish(initialApprovalTemplatesState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialApprovalTemplatesState);
    try {
      const result = await this.load.execute(this.access, this.search, pending.signal);
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
  private publish(state: ApprovalTemplatesState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
