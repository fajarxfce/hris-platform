import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveBalanceQuery } from "../../domain/entities/leave-balance-query";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import {
  initialLeaveAdjustmentCatalogState,
  type LeaveAdjustmentCatalogState,
} from "../models/leave-adjustment-catalog-state";

export class LeaveAdjustmentCatalogController {
  #state = initialLeaveAdjustmentCatalogState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LeaveUseCases["loadAdjustmentCatalog"],
    private readonly access: CompanyAccess,
    private readonly employee: string,
    private readonly query: LeaveBalanceQuery,
  ) {}
  getSnapshot = (): LeaveAdjustmentCatalogState => this.#state;
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
    this.publish(initialLeaveAdjustmentCatalogState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialLeaveAdjustmentCatalogState);
    try {
      const result = await this.load.execute(
        this.access,
        this.employee,
        this.query,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "ready", catalog: result.value, failure: null }
          : { stage: "unavailable", catalog: null, failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "unavailable",
          catalog: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: LeaveAdjustmentCatalogState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
