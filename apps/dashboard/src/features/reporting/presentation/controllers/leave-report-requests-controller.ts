import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveReportQuery } from "../../domain/entities/leave-report-request";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import {
  initialLeaveReportRequestsState,
  type LeaveReportRequestsState,
} from "../models/leave-report-requests-state";

export class LeaveReportRequestsController {
  #state = initialLeaveReportRequestsState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: ReportingUseCases["loadLeaveRequests"],
    private readonly access: CompanyAccess,
    private readonly query: LeaveReportQuery,
  ) {}
  getSnapshot = (): LeaveReportRequestsState => this.#state;
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
    this.publish(initialLeaveReportRequestsState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialLeaveReportRequestsState);
    try {
      const result = await this.load.execute(this.access, this.query, pending.signal);
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
  private publish(state: LeaveReportRequestsState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
