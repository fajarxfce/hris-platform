import type { Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import {
  initialAmendmentReadState,
  type PayrollAmendmentReadState,
} from "../models/payroll-amendment-review-state";

/** Owns one scoped amendment read; directory and detail bindings each create their own instance. */
export class PayrollAmendmentReader<T> {
  #state = initialAmendmentReadState<T>();
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: Readonly<{
      execute: (access: CompanyAccess, id: string, signal: AbortSignal) => Promise<Result<T>>;
    }>,
    private readonly access: CompanyAccess,
    private readonly id: string,
  ) {}
  getSnapshot = (): PayrollAmendmentReadState<T> => this.#state;
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
    this.publish(initialAmendmentReadState<T>());
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialAmendmentReadState<T>());
    try {
      const result = await this.load.execute(this.access, this.id, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", data: result.value, failure: null }
          : { stage: "unavailable", data: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          data: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: PayrollAmendmentReadState<T>): void {
    this.#state = state;
    for (const listener of [...this.#listeners]) listener();
  }
}
