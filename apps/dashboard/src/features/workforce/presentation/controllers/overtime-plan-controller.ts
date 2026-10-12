import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OvertimeIntervalInput } from "../../domain/entities/overtime-interval";
import {
  type OvertimePlanContext,
  overtimePlanContext,
} from "../../domain/entities/overtime-plan-context";
import {
  isOvertimeWorkDate,
  overtimePlanningReadDenied,
  overtimePlanningReviewChanged,
  overtimePlanWasRejected,
} from "../../domain/policies/overtime-planning-policy";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { initialOvertimePlanState, type OvertimePlanState } from "../models/overtime-plan-state";

type Submission = Readonly<{
  id: string;
  operation: OperationId;
  context: OvertimePlanContext;
  requested: OvertimeIntervalInput;
  reason: string;
}>;

export class OvertimePlanController {
  #state: OvertimePlanState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();

  constructor(
    private readonly actions: Pick<WorkforceUseCases, "reviewOvertimePlan" | "planOvertimeRequest">,
    private readonly access: CompanyAccess,
    initialDate: string,
    private readonly nextIdentifier: () => string,
  ) {
    this.#state = initialOvertimePlanState(initialDate);
  }

  getSnapshot = (): OvertimePlanState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    if (this.#active) return;
    this.#active = true;
    if (!this.access.permissions.includes("overtime.manage"))
      this.publish({
        ...this.#state,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.#submission = null;
    this.publish(initialOvertimePlanState(this.#state.workDate));
  };

  changeDate = (workDate: string): void => {
    if (
      !this.#active ||
      this.#submission ||
      this.#state.stage === "saved" ||
      !this.access.permissions.includes("overtime.manage")
    )
      return;
    this.#pending?.abort();
    this.#pending = null;
    this.publish({
      ...initialOvertimePlanState(workDate),
      failure: isOvertimeWorkDate(workDate)
        ? null
        : { code: "overtime_date_out_of_range", fields: {}, parameters: {} },
    });
  };
  selectEmployee = async (employeeId: string): Promise<void> => {
    if (
      !this.#active ||
      this.#submission ||
      this.#state.stage === "saved" ||
      !this.access.permissions.includes("overtime.manage")
    )
      return;
    this.publish({ ...initialOvertimePlanState(this.#state.workDate), employeeId });
    await this.refresh();
  };
  refresh = async (): Promise<void> => {
    const { employeeId, workDate } = this.#state;
    if (!this.#active || this.#submission || !employeeId || this.#state.stage === "saved") return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...initialOvertimePlanState(workDate), employeeId, stage: "loading" });
    try {
      const result = await this.actions.reviewOvertimePlan.execute(
        this.access,
        employeeId,
        workDate,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...this.#state, stage: "reviewing", review: result.value }
          : { ...this.#state, stage: "unavailable", failure: result.failure },
      );
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

  submit = async (input: OvertimeIntervalInput, reason: string): Promise<void> => {
    const { review, stage } = this.#state;
    if (!this.#active || stage !== "reviewing" || !review || this.#submission) return;
    this.#submission = Object.freeze({
      id: this.nextIdentifier(),
      operation: this.nextIdentifier() as OperationId,
      context: overtimePlanContext(review),
      reason,
      requested: Object.freeze({
        startsAt: input.startsAt,
        endsAt: input.endsAt,
        breakMinutes: input.breakMinutes,
      }),
    });
    await this.deliver(false);
  };
  retry = async (): Promise<void> => {
    if (this.#active && this.#state.stage === "unconfirmed") await this.deliver(true);
  };

  /** One confirmed command owns its ID, operation and reviewed facts until acknowledged. */
  private async deliver(wasUnconfirmed: boolean): Promise<void> {
    const submission = this.#submission;
    if (!submission) return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({
      ...this.#state,
      stage: "submitting",
      failure: null,
      operationId: submission.operation,
    });
    try {
      let result: Result<MutationReceipt>;
      try {
        result = await this.actions.planOvertimeRequest.execute(
          this.access,
          submission.operation,
          submission.id,
          submission.context,
          submission.requested,
          submission.reason,
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
          ...initialOvertimePlanState(this.#state.workDate),
          stage: "saved",
          receipt: result.value,
        });
        return;
      }
      const review = overtimePlanningReadDenied(result.failure) ? null : this.#state.review;
      if (!wasUnconfirmed && overtimePlanWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          review,
          operationId: null,
          failure: result.failure,
          stage: !review
            ? "unavailable"
            : overtimePlanningReviewChanged(result.failure)
              ? "conflict"
              : "reviewing",
        });
      } else
        this.publish({ ...this.#state, review, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }

  private publish(state: OvertimePlanState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
