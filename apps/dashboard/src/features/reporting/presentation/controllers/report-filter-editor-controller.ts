import type { OperationId } from "../../../../core/domain/identifiers";
import { failed, success } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  DeleteReportFilterChange,
  ReportFilterDefinition,
  SaveReportFilterChange,
} from "../../domain/entities/saved-report-filter";
import { reportFilterCommandWasRejected } from "../../domain/policies/saved-report-filter-policy";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import {
  initialReportFilterEditorState,
  type ReportFilterEditorState,
} from "../models/report-filter-editor-state";

type Submission = Readonly<
  { operation: OperationId } & (
    | { kind: "save"; input: SaveReportFilterChange }
    | { kind: "delete"; input: DeleteReportFilterChange }
  )
>;
export class ReportFilterEditorController {
  #state = initialReportFilterEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      ReportingUseCases,
      "getSavedFilter" | "saveFilter" | "deleteFilter"
    >,
    private readonly access: CompanyAccess,
    readonly creating: boolean,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): ReportFilterEditorState => this.#state;
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
    this.#submission = null;
    this.publish(initialReportFilterEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialReportFilterEditorState);
    try {
      const result = this.creating
        ? success(null)
        : await this.actions.getSavedFilter.execute(this.access, this.id, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialReportFilterEditorState, stage: "editing", filter: result.value }
          : { ...initialReportFilterEditorState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialReportFilterEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: ReportFilterDefinition): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing") return;
    const original = this.#state.filter;
    this.#submission = Object.freeze({
      kind: "save",
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        ...fields,
        parameters: Object.freeze({
          ...fields.parameters,
          companies: Object.freeze([...fields.parameters.companies]),
        }),
        id: original?.id ?? this.id,
        expectedVersion: original?.version ?? null,
      }),
    });
    await this.submit(false);
  };
  reviewDelete = (): void => {
    if (this.#active && this.#state.stage === "editing" && this.#state.filter)
      this.publish({ ...this.#state, stage: "confirming-delete", failure: null });
  };
  cancelDelete = (): void => {
    if (this.#state.stage === "confirming-delete")
      this.publish({ ...this.#state, stage: "editing" });
  };
  confirmDelete = async (): Promise<void> => {
    const original = this.#state.filter;
    if (!this.#active || this.#state.stage !== "confirming-delete" || !original) return;
    this.#submission = Object.freeze({
      kind: "delete",
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({ id: original.id, expectedVersion: original.version }),
    });
    await this.submit(false);
  };
  retry = async (): Promise<void> => {
    if (this.#active && this.#state.stage === "unconfirmed") await this.submit(true);
  };
  private async submit(wasUnconfirmed: boolean): Promise<void> {
    const submission = this.#submission;
    if (!submission) return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({
      ...this.#state,
      stage: "saving",
      command: submission.kind,
      failure: null,
      operationId: submission.operation,
    });
    try {
      const result = await (submission.kind === "save"
        ? this.actions.saveFilter.execute(
            this.access,
            submission.operation,
            submission.input,
            pending.signal,
          )
        : this.actions.deleteFilter.execute(
            this.access,
            submission.operation,
            submission.input,
            pending.signal,
          )
      ).catch(() => failed("unexpected_error"));
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#submission = null;
        this.publish({
          ...initialReportFilterEditorState,
          stage: "completed",
          command: submission.kind,
          receipt: result.value,
        });
      } else if (!wasUnconfirmed && reportFilterCommandWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          operationId: null,
          failure: result.failure,
          stage: [
            "stale_version",
            "report_filter_not_found",
            "report_filter_version_limit",
          ].includes(result.failure.code)
            ? "conflict"
            : "editing",
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } catch {
      // Also retain commands when an adapter throws synchronously before returning its promise.
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...this.#state,
          stage: "unconfirmed",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: ReportFilterEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
