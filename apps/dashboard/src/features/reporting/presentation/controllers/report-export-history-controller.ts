import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ReportExportCursor } from "../../domain/entities/report-export";
import type { ReportExportUseCases } from "../contracts/report-export-use-cases";
import {
  initialReportExportHistoryState,
  type ReportExportHistoryState,
} from "../models/report-export-state";
export class ReportExportHistoryController {
  #state = initialReportExportHistoryState;
  #active = false;
  #visible = true;
  #pending: AbortController | null = null;
  #before: ReportExportCursor | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly read: ReportExportUseCases["readExports"],
    private readonly access: CompanyAccess,
  ) {}
  getSnapshot = (): ReportExportHistoryState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (visible = this.#visible): void => {
    if (this.#active) return;
    this.#active = true;
    this.#visible = visible;
    if (visible) void this.load();
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.#before = null;
    this.publish(initialReportExportHistoryState);
  };
  setVisible = (visible: boolean): void => {
    if (this.#visible === visible) return;
    this.#visible = visible;
    this.#pending?.abort();
    this.#pending = null;
    this.publish(initialReportExportHistoryState);
    if (visible && this.#active) void this.load();
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || !this.#visible) return;
    this.#before = null;
    await this.load();
  };
  older = async (): Promise<void> => {
    if (!this.#active || !this.#visible || this.#pending || !this.#state.history?.next) return;
    this.#before = this.#state.history.next;
    await this.load();
  };
  private async load(): Promise<void> {
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...initialReportExportHistoryState, older: this.#before !== null });
    try {
      const result = await this.read.list(this.access, this.#before, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish({
        ...this.#state,
        stage: result.ok ? "ready" : "unavailable",
        history: result.ok ? result.value : null,
        failure: result.ok ? null : result.failure,
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...this.#state,
          stage: "unavailable",
          history: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: ReportExportHistoryState): void {
    this.#state = Object.freeze(state);
    for (const listener of this.#listeners) listener();
  }
}
