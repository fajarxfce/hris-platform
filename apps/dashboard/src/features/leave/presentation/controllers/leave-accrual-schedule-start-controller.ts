import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveAccrualScheduleId } from "../../domain/entities/leave-accrual-schedule";
import type { LeaveAccrualScheduleCreation } from "../../domain/entities/leave-accrual-schedule-command";
import type { LeaveAccrualScheduleSelection } from "../../domain/entities/leave-accrual-schedule-review";
import {
  canScheduleLeaveAccrual,
  leaveAccrualScheduleCommandRejected,
  leaveAccrualScheduleReviewChanged,
} from "../../domain/policies/leave-accrual-schedule-policy";
import { leaveBatchAccessLost } from "../../domain/policies/leave-batch-policy";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import {
  initialLeaveAccrualScheduleStartState,
  type LeaveAccrualScheduleStartState,
} from "../models/leave-accrual-schedule-state";

type Submission = Readonly<{ operation: OperationId; input: LeaveAccrualScheduleCreation }>;
/** Owns a reviewed selection and one immutable command until confirmation or workspace replacement. */
export class LeaveAccrualScheduleStartController {
  #state = initialLeaveAccrualScheduleStartState;
  #active = false;
  #visible = true;
  #pending: AbortController | null = null;
  #selection: LeaveAccrualScheduleSelection | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<LeaveUseCases, "reviewAccrualSchedule" | "scheduleAccrual">,
    private readonly access: CompanyAccess,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): LeaveAccrualScheduleStartState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    if (this.#active) return;
    this.#active = true;
    this.refresh();
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.#selection = null;
    this.#submission = null;
    this.publish(initialLeaveAccrualScheduleStartState);
  };
  setVisible = (visible: boolean): void => {
    if (this.#visible === visible) return;
    this.#visible = visible;
    if (!visible) {
      this.#pending?.abort();
      this.#pending = null;
      this.publish({
        ...this.#state,
        review: null,
        stage: this.#state.stage === "saving" ? "unconfirmed" : this.#state.stage,
      });
    } else if (
      this.#active &&
      !this.#submission &&
      this.#selection &&
      (["preparing", "reviewing"].includes(this.#state.stage) ||
        ["mfa_required", "mfa_setup_required", "recent_authentication_required"].includes(
          this.#state.failure?.code ?? "",
        ))
    ) {
      this.publish({ ...this.#state, stage: "editing", review: null, failure: null });
      void this.prepare(this.#selection);
    }
  };
  refresh = (): void => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    this.#pending = null;
    this.#selection = null;
    this.publish(
      canScheduleLeaveAccrual(this.access.permissions)
        ? initialLeaveAccrualScheduleStartState
        : {
            ...initialLeaveAccrualScheduleStartState,
            stage: "unavailable",
            failure: { code: "access_denied", fields: {}, parameters: {} },
          },
    );
  };
  prepare = async (selection: LeaveAccrualScheduleSelection): Promise<void> => {
    if (!this.#active || !this.#visible || this.#state.stage !== "editing" || this.#submission)
      return;
    this.#selection = Object.freeze({ ...selection });
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...this.#state, stage: "preparing", review: null, failure: null });
    try {
      const result = await this.actions.reviewAccrualSchedule.execute(
        this.access,
        this.#selection,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#selection = result.value.selection;
        this.publish({ ...this.#state, stage: "reviewing", review: result.value });
      } else {
        if (
          leaveBatchAccessLost(result.failure) &&
          !["mfa_required", "mfa_setup_required", "recent_authentication_required"].includes(
            result.failure.code,
          )
        )
          this.#selection = null;
        this.publish({
          ...this.#state,
          stage: leaveBatchAccessLost(result.failure) ? "unavailable" : "editing",
          failure: result.failure,
        });
      }
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...this.#state,
          stage: "editing",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  revise = (): void => {
    if (
      this.#active &&
      this.#visible &&
      !this.#submission &&
      ["reviewing", "conflict"].includes(this.#state.stage)
    ) {
      this.#selection = null;
      this.publish({ ...this.#state, stage: "editing", review: null, failure: null });
    }
  };
  save = async (reason: string): Promise<void> => {
    const review = this.#state.review;
    if (
      !this.#active ||
      !this.#visible ||
      this.#state.stage !== "reviewing" ||
      !review ||
      !canScheduleLeaveAccrual(this.access.permissions)
    )
      return;
    try {
      this.#submission = Object.freeze({
        operation: this.nextIdentifier() as OperationId,
        input: Object.freeze({
          id: this.nextIdentifier() as LeaveAccrualScheduleId,
          companyId: this.access.companyId,
          ...review.selection,
          expectedPolicyVersion: review.policyVersion,
          employeeIds: Object.freeze([...review.employeeIds]),
          reason: reason.trim(),
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
  retry = async (): Promise<void> => {
    if (this.#active && this.#visible && this.#state.stage === "unconfirmed")
      await this.submit(true);
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
        result = await this.actions.scheduleAccrual.execute(
          this.access,
          submission.operation,
          submission.input,
          pending.signal,
        );
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#submission = null;
        this.#selection = null;
        this.publish({
          ...initialLeaveAccrualScheduleStartState,
          stage: "saved",
          receipt: result.value,
          scheduleId: submission.input.id,
        });
      } else {
        const review = leaveBatchAccessLost(result.failure) ? null : this.#state.review;
        if (!wasUnconfirmed && leaveAccrualScheduleCommandRejected(result.failure)) {
          this.#submission = null;
          if (
            leaveBatchAccessLost(result.failure) &&
            !["mfa_required", "mfa_setup_required", "recent_authentication_required"].includes(
              result.failure.code,
            )
          )
            this.#selection = null;
          this.publish({
            ...this.#state,
            review,
            failure: result.failure,
            operationId: null,
            stage:
              review === null
                ? "unavailable"
                : leaveAccrualScheduleReviewChanged(result.failure)
                  ? "conflict"
                  : "reviewing",
          });
        } else
          this.publish({ ...this.#state, review, stage: "unconfirmed", failure: result.failure });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: LeaveAccrualScheduleStartState): void {
    this.#state = state;
    for (const listener of [...this.#listeners]) listener();
  }
}
