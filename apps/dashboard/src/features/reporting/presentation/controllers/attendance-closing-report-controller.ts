import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import {
  type AttendanceClosingReportState,
  initialAttendanceClosingReportState,
} from "../models/attendance-closing-report-state";

/** Owns one mounted account/company/date-window report; no shared report cache or polling. */
export class AttendanceClosingReportController {
  #state: AttendanceClosingReportState = initialAttendanceClosingReportState;
  #listeners = new Set<() => void>();
  #active = false;
  #pending: AbortController | null = null;

  constructor(
    private readonly load: ReportingUseCases["loadAttendanceClosings"],
    private readonly access: CompanyAccess,
    private readonly from: string,
    private readonly until: string,
  ) {}

  getSnapshot = (): AttendanceClosingReportState => this.#state;

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
    this.publish(initialAttendanceClosingReportState);
  };

  refresh = async (): Promise<void> => {
    if (!this.#active) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "loading", report: null, failure: null });
    try {
      const result = await this.load.execute(this.access, this.from, this.until, pending.signal);
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

  private publish(state: AttendanceClosingReportState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
