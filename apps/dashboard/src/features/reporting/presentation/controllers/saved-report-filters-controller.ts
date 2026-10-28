import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import {
  initialSavedReportFiltersState,
  type SavedReportFiltersState,
} from "../models/saved-report-filters-state";

/** Owns one mounted account/company filter directory; no shared report cache or polling. */
export class SavedReportFiltersController {
  #state: SavedReportFiltersState = initialSavedReportFiltersState;
  #listeners = new Set<() => void>();
  #active = false;
  #pending: AbortController | null = null;

  constructor(
    private readonly load: ReportingUseCases["listSavedFilters"],
    private readonly access: CompanyAccess,
  ) {}

  getSnapshot = (): SavedReportFiltersState => this.#state;

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
    this.publish(initialSavedReportFiltersState);
  };

  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", items: [], failure: null });
    try {
      const result = await this.load.execute(this.access, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", items: result.value, failure: null }
          : { stage: "unavailable", items: [], failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending) {
        this.publish({
          stage: "unavailable",
          items: [],
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };

  private publish(state: SavedReportFiltersState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
