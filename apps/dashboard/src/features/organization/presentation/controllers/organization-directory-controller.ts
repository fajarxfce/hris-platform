import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OrganizationUnitSearchInput } from "../../domain/entities/organization-unit-search";
import type { OrganizationUseCases } from "../contracts/organization-use-cases";
import {
  initialOrganizationDirectoryState,
  type OrganizationDirectoryState,
} from "../models/organization-directory-state";

/** One applied page owns one request. Company changes discard the owner and its data. */
export class OrganizationDirectoryController {
  #state: OrganizationDirectoryState = initialOrganizationDirectoryState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  private readonly search: OrganizationUnitSearchInput;
  constructor(
    private readonly load: OrganizationUseCases["loadUnits"],
    private readonly access: CompanyAccess,
    search: OrganizationUnitSearchInput,
  ) {
    this.search = Object.freeze({ ...search });
  }

  getSnapshot = (): OrganizationDirectoryState => this.#state;
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
    this.publish(initialOrganizationDirectoryState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", page: null, failure: null });
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
  private publish(state: OrganizationDirectoryState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
