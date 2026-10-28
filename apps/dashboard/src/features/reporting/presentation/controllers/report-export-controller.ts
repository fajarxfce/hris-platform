import type { OperationId } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ChangeReportExport, StartReportExport } from "../../domain/entities/report-export";
import { canReadReportKind } from "../../domain/policies/report-catalogue-policy";
import {
  canCancelReportExport,
  canResumeReportExport,
  reportExportCommandWasRejected,
  reportExportExpired,
} from "../../domain/policies/report-export-policy";
import type { ReportExportUseCases } from "../contracts/report-export-use-cases";
import { initialReportExportState, type ReportExportState } from "../models/report-export-state";

type Submission = Readonly<
  { operation: OperationId } & (
    | { kind: "start"; input: StartReportExport }
    | { kind: "cancel" | "resume"; input: ChangeReportExport }
  )
>;
/** One account/company screen owns reads, one retained command and one cancellable file operation. */
export class ReportExportController {
  #state = initialReportExportState;
  #active = false;
  #visible = true;
  #pending: AbortController | null = null;
  #downloading: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: ReportExportUseCases,
    private readonly access: CompanyAccess,
    private readonly id: string | null,
    private readonly nextIdentifier: () => string,
    private readonly now: () => number = Date.now,
  ) {}
  getSnapshot = (): ReportExportState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (visible = this.#visible): void => {
    if (this.#active) return;
    this.#active = true;
    this.#visible = visible;
    if (visible) void this.refresh();
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.#downloading?.abort();
    this.#downloading = null;
    this.#submission = null;
    this.publish(initialReportExportState);
  };
  setVisible = (visible: boolean): void => {
    if (visible === this.#visible) return;
    this.#visible = visible;
    this.#pending?.abort();
    this.#pending = null;
    this.#downloading?.abort();
    this.#downloading = null;
    if (this.#submission)
      this.publish({
        ...this.#state,
        stage: "unconfirmed",
        detail: null,
        confirmation: null,
        download: "idle",
      });
    else if (this.#state.stage !== "completed") this.publish({ ...initialReportExportState });
    if (visible && this.#active && !this.#submission && this.#state.stage !== "completed")
      void this.refresh();
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || !this.#visible || this.#submission) return;
    this.#pending?.abort();
    this.#downloading?.abort();
    this.#downloading = null;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({
      ...initialReportExportState,
      receipt: this.#state.receipt,
      command: this.#state.command,
    });
    try {
      const result = this.id
        ? await this.actions.readExports.get(this.access, this.id, pending.signal)
        : success(null);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish({
        ...this.#state,
        stage: result.ok ? "ready" : "unavailable",
        detail: result.ok ? result.value : null,
        failure: result.ok ? null : result.failure,
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...this.#state,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  start = async (input: StartReportExport): Promise<void> => {
    if (!this.available() || this.id || !canReadReportKind(this.access.permissions, input.kind))
      return;
    this.#submission = Object.freeze({
      kind: "start",
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        ...input,
        parameters: Object.freeze({
          ...input.parameters,
          companies: Object.freeze([...input.parameters.companies]),
        }),
      }),
    });
    await this.submit(false);
  };
  review = (kind: "cancel" | "resume"): void => {
    const detail = this.#state.detail;
    if (
      !this.available() ||
      !detail ||
      (kind === "cancel"
        ? !canCancelReportExport(detail)
        : !canReadReportKind(this.access.permissions, detail.selection.kind) ||
          !canResumeReportExport(detail, this.now()))
    )
      return;
    this.publish({ ...this.#state, confirmation: kind, failure: null });
  };
  dismiss = (): void => {
    if (this.#state.stage === "ready") this.publish({ ...this.#state, confirmation: null });
  };
  confirm = async (): Promise<void> => {
    const detail = this.#state.detail,
      kind = this.#state.confirmation;
    if (!this.available() || !detail || !kind) return;
    if (kind === "resume" && !canResumeReportExport(detail, this.now())) {
      this.publish({
        ...this.#state,
        confirmation: null,
        failure: { code: "report_export_expired", fields: {}, parameters: {} },
      });
      return;
    }
    this.#submission = Object.freeze({
      kind,
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({ id: detail.id, expectedVersion: detail.version }),
    });
    await this.submit(false);
  };
  retry = async (): Promise<void> => {
    if (this.#active && this.#visible && this.#state.stage === "unconfirmed")
      await this.submit(true);
  };
  cancelDownload = (): void => {
    this.#downloading?.abort();
    this.#downloading = null;
    this.publish({ ...this.#state, download: "idle" });
  };
  download = async (): Promise<void> => {
    const detail = this.#state.detail;
    if (
      !this.available() ||
      !detail ||
      detail.status !== "READY" ||
      reportExportExpired(detail, this.now()) ||
      !canReadReportKind(this.access.permissions, detail.selection.kind)
    )
      return;
    const pending = new AbortController();
    this.#downloading = pending;
    this.publish({ ...this.#state, download: "downloading", failure: null });
    try {
      const result = await this.actions.downloadExport.execute(
        this.access,
        detail.id,
        pending.signal,
      );
      if (pending.signal.aborted || this.#downloading !== pending) return;
      this.publish(
        result.ok
          ? { ...this.#state, download: "started" }
          : {
              ...this.#state,
              stage: "unavailable",
              detail: null,
              download: "idle",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#downloading === pending)
        this.publish({
          ...this.#state,
          stage: "unavailable",
          detail: null,
          download: "idle",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#downloading === pending) this.#downloading = null;
    }
  };
  private available(): boolean {
    return (
      this.#active &&
      this.#visible &&
      this.#state.stage === "ready" &&
      !this.#pending &&
      !this.#submission &&
      !this.#downloading
    );
  }
  private async submit(wasUnconfirmed: boolean): Promise<void> {
    const command = this.#submission;
    if (!command) return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({
      ...this.#state,
      stage: "saving",
      command: command.kind,
      operationId: command.operation,
      confirmation: null,
      receipt: null,
      failure: null,
    });
    let reload = false;
    try {
      const result = await (command.kind === "start"
        ? this.actions.manageExports.start(
            this.access,
            command.operation,
            command.input,
            pending.signal,
          )
        : this.actions.manageExports.change(
            this.access,
            command.operation,
            command.kind,
            command.input,
            pending.signal,
          )
      ).catch(() => failed("unexpected_error"));
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#submission = null;
        this.publish({
          ...initialReportExportState,
          stage: "completed",
          command: command.kind,
          receipt: result.value,
        });
        reload = command.kind !== "start";
      } else if (!wasUnconfirmed && reportExportCommandWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: this.id ? "conflict" : "ready",
          detail: null,
          operationId: null,
          failure: result.failure,
        });
      } else
        this.publish({
          ...this.#state,
          stage: "unconfirmed",
          detail: null,
          failure: result.failure,
        });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...this.#state,
          stage: "unconfirmed",
          detail: null,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
    if (reload) await this.refresh();
  }
  private publish(state: ReportExportState): void {
    this.#state = Object.freeze(state);
    for (const listener of this.#listeners) listener();
  }
}
