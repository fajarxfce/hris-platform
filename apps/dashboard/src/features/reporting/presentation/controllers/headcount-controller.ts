import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { type HeadcountState, initialHeadcountState } from "../models/headcount-state";

/** Owns one mounted account/company/date report; no process-wide report cache or polling. */
export class HeadcountController {
  #state: HeadcountState = initialHeadcountState;
  #listeners = new Set<() => void>();
  #active = false;
  #pending: AbortController | null = null;

  constructor(
    private readonly load: ReportingUseCases["loadHeadcount"],
    private readonly access: CompanyAccess,
    private readonly asOf: string,
  ) {}

  getSnapshot = (): HeadcountState => this.#state;

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
    this.publish(initialHeadcountState);
  };

  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", report: null, failure: null });
    try {
      const result = await this.load.execute(this.access, this.asOf, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", report: result.value, failure: null }
          : { stage: "unavailable", report: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending) {
        this.publish({
          stage: "unavailable",
          report: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };

  private publish(state: HeadcountState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
