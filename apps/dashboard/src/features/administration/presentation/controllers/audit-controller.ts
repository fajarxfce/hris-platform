import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AuditSearch } from "../../domain/entities/audit-search";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import { type AuditState, initialAuditState } from "../models/audit-state";

/** Owns one page and one request. URL navigation replaces this owner instead of accumulating pages. */
export class AuditController {
  #state: AuditState = initialAuditState;
  #listeners = new Set<() => void>();
  #active = false;
  #pending: AbortController | null = null;

  constructor(
    private readonly search: AdministrationUseCases["searchAudit"],
    private readonly access: CompanyAccess,
    private readonly query: AuditSearch,
  ) {
    this.query = Object.freeze({ ...query });
  }

  getSnapshot = (): AuditState => this.#state;

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
    this.publish(initialAuditState);
  };

  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", page: null, selected: null, failure: null });
    try {
      const result = await this.search.execute(this.access, this.query, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", page: result.value, selected: null, failure: null }
          : { stage: "unavailable", page: null, selected: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending) {
        this.publish({
          stage: "unavailable",
          page: null,
          selected: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };

  openEvent = (id: string): void => {
    if (!this.#active || this.#state.stage !== "ready") return;
    const selected = this.#state.page?.items.find((event) => event.id === id);
    if (selected) this.publish({ ...this.#state, selected });
  };

  closeEvent = (): void => {
    if (this.#state.selected) this.publish({ ...this.#state, selected: null });
  };

  private publish(state: AuditState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
