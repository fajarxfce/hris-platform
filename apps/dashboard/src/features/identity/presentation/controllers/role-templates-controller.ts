import type { CompanyAccess } from "../../domain/entities/session";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import { initialRoleTemplatesState, type RoleTemplatesState } from "../models/role-templates-state";

export class RoleTemplatesController {
  #state: RoleTemplatesState = initialRoleTemplatesState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: IdentityAdministrationUseCases["loadRoleTemplates"],
    private readonly access: CompanyAccess,
    private readonly after: string | null,
  ) {}
  getSnapshot = (): RoleTemplatesState => this.#state;
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
    this.publish(initialRoleTemplatesState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", page: null, failure: null });
    try {
      const result = await this.load.execute(this.access, this.after, pending.signal);
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
  private publish(state: RoleTemplatesState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
