import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveBalanceAdjustment } from "../../domain/entities/leave-balance-adjustment";
import { leaveBalanceAdjustmentWasRejected } from "../../domain/policies/leave-balance-adjustment-policy";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import {
  initialLeaveBalanceAdjustmentState,
  type LeaveBalanceAdjustmentState,
} from "../models/leave-balance-adjustment-state";

type Submission = Readonly<{ operation: OperationId; input: LeaveBalanceAdjustment }>;
export class LeaveBalanceAdjustmentController {
  #state = initialLeaveBalanceAdjustmentState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<LeaveUseCases, "reviewBalanceAdjustment" | "adjustBalance">,
    private readonly access: CompanyAccess,
    private readonly employee: string,
    private readonly type: string,
    private readonly year: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): LeaveBalanceAdjustmentState => this.#state;
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
    this.publish(initialLeaveBalanceAdjustmentState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialLeaveBalanceAdjustmentState);
    try {
      const result = await this.actions.reviewBalanceAdjustment.execute(
        this.access,
        this.employee,
        this.type,
        this.year,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialLeaveBalanceAdjustmentState, stage: "editing", review: result.value }
          : {
              ...initialLeaveBalanceAdjustmentState,
              stage: "unavailable",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialLeaveBalanceAdjustmentState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (days: string, reason: string): Promise<void> => {
    const review = this.#state.review;
    if (!this.#active || this.#state.stage !== "editing" || !review) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        employeeId: review.employee.id,
        typeId: review.typeId,
        year: review.balance.year,
        expectedVersion: review.balance.version,
        days,
        reason,
      }),
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
        result = await this.actions.adjustBalance.execute(
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
        this.publish({
          ...initialLeaveBalanceAdjustmentState,
          stage: "saved",
          receipt: result.value,
        });
        return;
      }
      const review = [
        "access_denied",
        "company_access_denied",
        "employee_not_found",
        "leave_type_not_found",
        "self_adjustment_denied",
        "authentication_required",
        "session_revoked",
        "unauthenticated",
        "mfa_required",
        "mfa_setup_required",
        "recent_authentication_required",
      ].includes(result.failure.code)
        ? null
        : this.#state.review;
      if (!wasUnconfirmed && leaveBalanceAdjustmentWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          review,
          failure: result.failure,
          operationId: null,
          stage: !review
            ? "unavailable"
            : ["stale_balance_version", "leave_year_closed", "leave_type_unavailable"].includes(
                  result.failure.code,
                )
              ? "conflict"
              : "editing",
        });
      } else
        this.publish({ ...this.#state, review, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: LeaveBalanceAdjustmentState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
