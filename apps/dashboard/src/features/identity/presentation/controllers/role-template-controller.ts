import type { CompanyAccess } from "../../domain/entities/session";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import { initialRoleTemplateState, type RoleTemplateState } from "../models/role-template-state";

export class RoleTemplateController {
  #active = false;
  #pending: AbortController | null = null;
  #state = initialRoleTemplateState;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: IdentityAdministrationUseCases["loadRoleTemplate"],
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly historyAfter: string | null,
  ) {}
  getSnapshot = (): RoleTemplateState => this.#state;
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
    this.publish(initialRoleTemplateState);
  };
  selectRevision = (revision: string): void => {
    if (!this.#active || this.#state.stage !== "ready") return;
    const selected = this.#state.review?.history.items.find(
      (item) => String(item.revision) === revision,
    );
    if (selected) this.publish({ ...this.#state, selectedRevision: selected });
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialRoleTemplateState);
    try {
      const result = await this.load.execute(
        this.access,
        this.id,
        this.historyAfter,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialRoleTemplateState, stage: "ready", review: result.value }
          : { ...initialRoleTemplateState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialRoleTemplateState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: RoleTemplateState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
