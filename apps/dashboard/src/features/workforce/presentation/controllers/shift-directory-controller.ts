import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ShiftSearch } from "../../domain/entities/shift-search";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import {
  initialShiftDirectoryState,
  type ShiftDirectoryState,
} from "../models/shift-directory-state";

/** One applied page owns one request. Company changes discard the owner and its data. */
export class ShiftDirectoryController {
  #state: ShiftDirectoryState = initialShiftDirectoryState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  private readonly search: ShiftSearch;
  constructor(
    private readonly load: WorkforceUseCases["loadShifts"],
    private readonly access: CompanyAccess,
    search: ShiftSearch,
  ) {
    this.search = Object.freeze({ ...search });
  }

  getSnapshot = (): ShiftDirectoryState => this.#state;
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
    this.publish(initialShiftDirectoryState);
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
  private publish(state: ShiftDirectoryState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
