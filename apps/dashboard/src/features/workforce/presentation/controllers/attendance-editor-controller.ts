import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AttendanceCorrectionChange } from "../../domain/entities/attendance-correction-change";
import type { AttendanceReviewChange } from "../../domain/entities/attendance-review-change";
import {
  attendanceChangeWasRejected,
  canCorrectAttendance,
  canReviewAttendance,
} from "../../domain/policies/attendance-policy";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import {
  type AttendanceEditorMode,
  type AttendanceEditorState,
  initialAttendanceEditorState,
} from "../models/attendance-editor-state";

type Actions = Pick<
  WorkforceUseCases,
  "loadEmployeeAttendance" | "reviewAttendance" | "correctAttendance"
>;
type Submission = Readonly<{
  operation: OperationId;
  send: (signal: AbortSignal) => Promise<Result<MutationReceipt>>;
}>;

/** One route owns its observed evidence and retains the same command through uncertain outcomes. */
export class AttendanceEditorController {
  #state = initialAttendanceEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Actions,
    private readonly access: CompanyAccess,
    readonly employeeId: string,
    readonly date: string,
    readonly mode: AttendanceEditorMode,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): AttendanceEditorState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    if (!this.#active) {
      this.#active = true;
      void this.refresh();
    }
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.#submission = null;
    this.publish(initialAttendanceEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission !== null) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialAttendanceEditorState);
    try {
      const allowed =
        this.mode.kind === "review"
          ? canReviewAttendance(this.access.permissions)
          : canCorrectAttendance(this.access.permissions);
      const result = allowed
        ? await this.actions.loadEmployeeAttendance.execute(
            this.access,
            this.employeeId,
            { from: this.date, until: this.date },
            pending.signal,
          )
        : failed("access_denied");
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (!result.ok) {
        this.publish({
          ...initialAttendanceEditorState,
          stage: "unavailable",
          failure: result.failure,
        });
        return;
      }
      const day = result.value.days[0] ?? null;
      const entryId = this.mode.kind === "review" ? this.mode.entryId : null;
      const entry = day?.entries.find((item) => item.id === entryId) ?? null;
      const code = !day
        ? "attendance_not_found"
        : this.mode.kind === "review"
          ? !entry
            ? "attendance_not_found"
            : entry.status !== "PENDING"
              ? "stale_version"
              : null
          : day.pendingCount !== 0
            ? "attendance_verification_required"
            : null;
      this.publish({
        ...initialAttendanceEditorState,
        employee: result.value.employee,
        day,
        entry,
        stage: code ? "conflict" : "editing",
        failure: code ? { code, fields: {}, parameters: {} } : null,
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialAttendanceEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  review = async (fields: Pick<AttendanceReviewChange, "decision" | "reason">): Promise<void> => {
    if (
      !this.#active ||
      this.#state.stage !== "editing" ||
      this.mode.kind !== "review" ||
      !this.#state.entry
    )
      return;
    const operation = this.nextIdentifier() as OperationId;
    const change = Object.freeze({
      ...fields,
      entryId: this.#state.entry.id,
      version: this.#state.entry.version,
    });
    this.#submission = Object.freeze({
      operation,
      send: (signal: AbortSignal) =>
        this.actions.reviewAttendance.execute(this.access, operation, change, signal),
    });
    await this.submit(false);
  };
  correct = async (
    fields: Pick<AttendanceCorrectionChange, "clockIn" | "clockOut" | "breakMinutes" | "reason">,
  ): Promise<void> => {
    if (
      !this.#active ||
      this.#state.stage !== "editing" ||
      this.mode.kind !== "correction" ||
      !this.#state.day
    )
      return;
    const operation = this.nextIdentifier() as OperationId;
    const change = Object.freeze({
      ...fields,
      employeeId: this.employeeId,
      workDate: this.date,
      expectedVersion: this.#state.day.correction?.version ?? null,
    });
    this.#submission = Object.freeze({
      operation,
      send: (signal: AbortSignal) =>
        this.actions.correctAttendance.execute(this.access, operation, change, signal),
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
      failure: null,
      operationId: submission.operation,
    });
    try {
      let result: Result<MutationReceipt>;
      try {
        result = await submission.send(pending.signal);
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: "saved",
          receipt: result.value,
          failure: null,
          operationId: null,
        });
      } else if (!wasUnconfirmed && attendanceChangeWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: [
            "stale_version",
            "attendance_not_found",
            "attendance_verification_required",
            "attendance_day_corrected",
            "attendance_sequence_conflict",
            "work_period_locked",
          ].includes(result.failure.code)
            ? "conflict"
            : "editing",
          failure: result.failure,
          operationId: null,
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: AttendanceEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
