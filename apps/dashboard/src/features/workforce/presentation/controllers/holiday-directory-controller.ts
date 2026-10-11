import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { HolidayRange } from "../../domain/entities/holiday-range";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import {
  type HolidayDirectoryState,
  initialHolidayDirectoryState,
} from "../models/holiday-directory-state";

/** One applied range owns one request. Replacing the company or range cancels that owner. */
export class HolidayDirectoryController {
  #state: HolidayDirectoryState = initialHolidayDirectoryState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  private readonly range: HolidayRange;
  constructor(
    private readonly load: WorkforceUseCases["loadHolidays"],
    private readonly access: CompanyAccess,
    range: HolidayRange,
  ) {
    this.range = Object.freeze({ ...range });
  }
  getSnapshot = (): HolidayDirectoryState => this.#state;
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
    this.publish(initialHolidayDirectoryState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", holidays: null, failure: null });
    try {
      const result = await this.load.execute(this.access, this.range, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", holidays: result.value, failure: null }
          : { stage: "unavailable", holidays: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          holidays: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: HolidayDirectoryState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
