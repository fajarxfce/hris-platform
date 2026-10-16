import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  AttendanceAdjustmentChange,
  AttendanceEvidenceDecision,
} from "../../domain/entities/attendance-adjustment-change";
import {
  attendanceAdjustmentAccessLost,
  attendanceAdjustmentCommandRejected,
  attendanceAdjustmentReviewChanged,
} from "../../domain/policies/attendance-adjustment-policy";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import {
  type AttendanceAdjustmentState,
  initialAttendanceAdjustmentState,
} from "../models/attendance-adjustment-state";

type Submission = Readonly<{ operation: OperationId; change: AttendanceAdjustmentChange }>;
type Fields = Pick<
  AttendanceAdjustmentChange,
  "clockIn" | "clockOut" | "breakMinutes" | "reason"
> & { decisions: readonly AttendanceEvidenceDecision[] };

/** Owns one reviewed day and preserves its original command through response loss. */
export class AttendanceAdjustmentController {
  #state = initialAttendanceAdjustmentState;
  #active = false;
  #visible = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      WorkforceUseCases,
      "loadAttendanceAdjustmentReview" | "adjustClosedAttendance"
    >,
    private readonly access: CompanyAccess,
    readonly employee: string,
    readonly date: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): AttendanceAdjustmentState => this.#state;
  subscribe = (listener: () => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = () => {
    if (!this.#active) {
      this.#active = true;
      void this.refresh();
    }
  };
  deactivate = () => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.#submission = null;
    this.publish(initialAttendanceAdjustmentState);
  };
  setVisible = (visible: boolean) => {
    if (this.#visible === visible) return;
    this.#visible = visible;
    if (!visible && this.#state.stage === "loading") {
      this.#pending?.abort();
      this.#pending = null;
    }
    if (visible && this.#active && this.#state.stage === "loading" && !this.#pending)
      void this.refresh();
  };
  refresh = async () => {
    if (!this.#active || !this.#visible || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialAttendanceAdjustmentState);
    try {
      const result = await this.actions.loadAttendanceAdjustmentReview.execute(
        this.access,
        this.employee,
        this.date,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialAttendanceAdjustmentState, stage: "reviewing", review: result.value }
          : { ...initialAttendanceAdjustmentState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialAttendanceAdjustmentState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: Fields) => {
    const review = this.#state.review;
    if (
      !this.#active ||
      !this.#visible ||
      this.#state.stage !== "reviewing" ||
      !review?.canCorrect ||
      (review.attendance.pendingCount > 0 && !review.canReviewEvidence)
    )
      return;
    const pending = review.attendance.entries.filter((entry) => entry.status === "PENDING");
    if (
      pending.length !== fields.decisions.length ||
      fields.decisions.some(
        (decision) =>
          !pending.some(
            (entry) => entry.id === decision.eventId && entry.version === decision.expectedVersion,
          ),
      )
    ) {
      this.publish({
        ...this.#state,
        failure: { code: "attendance_adjustment_evidence_changed", fields: {}, parameters: {} },
      });
      return;
    }
    try {
      this.#submission = Object.freeze({
        operation: this.nextIdentifier() as OperationId,
        change: Object.freeze({
          ...fields,
          companyId: this.access.companyId,
          employeeId: review.employee.id,
          workDate: review.current.workDate,
          reviewRevision: review.revision,
          expectedAdjustmentRevision: review.adjustmentRevision,
          decisions: Object.freeze(
            fields.decisions.map((decision) => Object.freeze({ ...decision })),
          ),
        }),
      });
    } catch {
      this.publish({
        ...this.#state,
        failure: { code: "unexpected_error", fields: {}, parameters: {} },
      });
      return;
    }
    await this.submit(false);
  };
  retry = async () => {
    if (this.#active && this.#visible && this.#state.stage === "unconfirmed")
      await this.submit(true);
  };
  private async submit(wasUnconfirmed: boolean) {
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
        result = await this.actions.adjustClosedAttendance.execute(
          this.access,
          submission.operation,
          submission.change,
          pending.signal,
        );
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
          operationId: null,
          failure: null,
        });
      } else {
        const review = attendanceAdjustmentAccessLost(result.failure) ? null : this.#state.review;
        if (!wasUnconfirmed && attendanceAdjustmentCommandRejected(result.failure)) {
          this.#submission = null;
          this.publish({
            ...this.#state,
            review,
            stage:
              review === null
                ? "unavailable"
                : attendanceAdjustmentReviewChanged(result.failure)
                  ? "conflict"
                  : "reviewing",
            failure: result.failure,
            operationId: null,
          });
        } else
          this.publish({ ...this.#state, review, stage: "unconfirmed", failure: result.failure });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: AttendanceAdjustmentState) {
    this.#state = state;
    for (const listener of [...this.#listeners]) listener();
  }
}
