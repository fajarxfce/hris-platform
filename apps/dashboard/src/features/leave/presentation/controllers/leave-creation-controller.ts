import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { type Failure, failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveEvidenceOption } from "../../domain/entities/leave-evidence-option";
import type { LeavePreparationEmployee } from "../../domain/entities/leave-preparation-employee";
import type { RequestedLeaveDay } from "../../domain/entities/leave-request-selection";
import type { LeaveRequestSubmission } from "../../domain/entities/leave-request-submission";
import type { LeaveRequestType } from "../../domain/entities/leave-request-type";
import {
  canPrepareLeave,
  leavePreparationAccessLost,
  leavePreparationChanged,
  leaveSubmissionWasRejected,
  prepareLeaveSubmission,
} from "../../domain/policies/leave-preparation-policy";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import {
  initialLeaveCreationState,
  type LeaveCreationState,
  leaveCreationIsLocked,
} from "../models/leave-creation-state";

type Submission = Readonly<{ operation: OperationId; input: LeaveRequestSubmission }>;

export class LeaveCreationController {
  #state = initialLeaveCreationState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();

  constructor(
    private readonly actions: Pick<LeaveUseCases, "previewRequest" | "submitRequest">,
    private readonly access: CompanyAccess,
    private readonly nextIdentifier: () => string,
  ) {}

  getSnapshot = (): LeaveCreationState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    if (this.#active) return;
    this.#active = true;
    if (!canPrepareLeave(this.access.permissions))
      this.withdrawAccess({ code: "access_denied", fields: {}, parameters: {} });
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.#submission = null;
    this.publish(initialLeaveCreationState);
  };

  selectEmployee = (employee: LeavePreparationEmployee): void => {
    if (
      !this.#active ||
      leaveCreationIsLocked(this.#state) ||
      !canPrepareLeave(this.access.permissions)
    )
      return;
    if (employee.companyId !== this.access.companyId) return;
    this.#pending?.abort();
    this.#pending = null;
    this.publish({
      ...initialLeaveCreationState,
      employee: Object.freeze({ ...employee }),
      type: this.#state.type,
    });
  };
  selectType = (type: LeaveRequestType, from: string): void => {
    if (!this.#active || leaveCreationIsLocked(this.#state) || this.#state.stage === "unavailable")
      return;
    if (type.companyId !== this.access.companyId || type.asOf !== from) return;
    this.#pending?.abort();
    this.#pending = null;
    this.publish({ ...this.#state, stage: "editing", review: null, type, failure: null });
  };
  changeDates = (from: string): void => {
    if (!this.#active || leaveCreationIsLocked(this.#state) || this.#state.stage === "unavailable")
      return;
    this.#pending?.abort();
    this.#pending = null;
    this.publish({
      ...this.#state,
      stage: "editing",
      review: null,
      type: this.#state.type?.asOf === from ? this.#state.type : null,
      failure: null,
    });
  };
  addEvidence = (item: LeaveEvidenceOption): void => {
    if (!this.#active || leaveCreationIsLocked(this.#state) || this.#state.stage === "unavailable")
      return;
    if (item.companyId !== this.access.companyId || item.employeeId !== this.#state.employee?.id)
      return;
    if (this.#state.evidence.some((value) => value.revisionId === item.revisionId)) return;
    if (this.#state.evidence.length === 3) {
      this.publish({
        ...this.#state,
        failure: { code: "invalid_leave_attachments", fields: {}, parameters: {} },
      });
      return;
    }
    this.publish({
      ...this.#state,
      evidence: Object.freeze([...this.#state.evidence, Object.freeze({ ...item })]),
      failure: null,
    });
  };
  removeEvidence = (revisionId: string): void => {
    if (!this.#active || leaveCreationIsLocked(this.#state)) return;
    this.publish({
      ...this.#state,
      evidence: Object.freeze(
        this.#state.evidence.filter((item) => item.revisionId !== revisionId),
      ),
    });
  };

  withdrawAccess = (failure: Failure): void => {
    if (!this.#active || !leavePreparationAccessLost(failure)) return;
    if (this.#submission) {
      this.publish({
        ...this.#state,
        employee: null,
        type: null,
        review: null,
        evidence: initialLeaveCreationState.evidence,
        failure,
      });
      return;
    }
    this.#pending?.abort();
    this.#pending = null;
    this.publish({ ...initialLeaveCreationState, stage: "unavailable", failure });
  };
  preview = async (days: readonly RequestedLeaveDay[]): Promise<void> => {
    const { employee, type, stage } = this.#state;
    if (
      !this.#active ||
      leaveCreationIsLocked(this.#state) ||
      stage === "previewing" ||
      stage === "unavailable" ||
      !employee ||
      !type
    )
      return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...this.#state, stage: "previewing", review: null, failure: null });
    try {
      const result = await this.actions.previewRequest.execute(
        this.access,
        {
          employeeId: employee.id,
          typeId: type.id,
          days,
        },
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) this.publish({ ...this.#state, stage: "reviewing", review: result.value });
      else if (leavePreparationAccessLost(result.failure)) this.withdrawAccess(result.failure);
      else this.publish({ ...this.#state, stage: "editing", failure: result.failure });
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
  confirm = async (reason: string): Promise<void> => {
    const { review, stage, evidence } = this.#state;
    if (!this.#active || stage !== "reviewing" || !review || this.#submission) return;
    const operation = this.nextIdentifier() as OperationId;
    const prepared = prepareLeaveSubmission(
      this.access,
      operation,
      this.nextIdentifier(),
      review,
      reason,
      evidence,
    );
    if (!prepared.ok) {
      this.publish({ ...this.#state, failure: prepared.failure });
      return;
    }
    this.#submission = Object.freeze({ operation, input: prepared.value });
    await this.deliver(false);
  };
  retry = async (): Promise<void> => {
    if (this.#active && this.#state.stage === "unconfirmed") await this.deliver(true);
  };

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
        result = await this.actions.submitRequest.execute(
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
        this.publish({ ...initialLeaveCreationState, stage: "saved", receipt: result.value });
        return;
      }
      const retained = leavePreparationAccessLost(result.failure)
        ? {
            ...this.#state,
            employee: null,
            type: null,
            review: null,
            evidence: initialLeaveCreationState.evidence,
          }
        : this.#state;
      if (!wasUnconfirmed && leaveSubmissionWasRejected(result.failure)) {
        this.#submission = null;
        const changed = leavePreparationChanged(result.failure);
        this.publish({
          ...retained,
          review: changed ? null : retained.review,
          operationId: null,
          failure: result.failure,
          stage: !retained.employee ? "unavailable" : changed ? "editing" : "reviewing",
        });
      } else this.publish({ ...retained, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }

  private publish(state: LeaveCreationState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
