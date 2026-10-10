import type { CompanyAccess } from "../../domain/entities/session";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import { type CompanyMemberState, initialCompanyMemberState } from "../models/company-member-state";

export class CompanyMemberController {
  #state: CompanyMemberState = initialCompanyMemberState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: IdentityAdministrationUseCases["loadCompanyMember"],
    private readonly access: CompanyAccess,
    private readonly memberId: string,
  ) {}
  getSnapshot = (): CompanyMemberState => this.#state;
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
    this.publish(initialCompanyMemberState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", grant: null, failure: null });
    try {
      const result = await this.load.execute(this.access, this.memberId, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", grant: result.value, failure: null }
          : { stage: "unavailable", grant: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          grant: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: CompanyMemberState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
