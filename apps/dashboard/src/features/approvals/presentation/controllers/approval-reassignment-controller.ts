import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalReassignmentSelection } from "../../domain/entities/approval-reassignment";
import type { ApprovalRequest } from "../../domain/entities/approval-request";
import { approvalReassignmentWasRejected } from "../../domain/policies/approval-reassignment-policy";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import {
  type ApprovalReassignmentState,
  initialApprovalReassignmentState,
} from "../models/approval-reassignment-state";

type Submission = Readonly<{
  operation: OperationId;
  request: ApprovalRequest;
  selection: ApprovalReassignmentSelection;
}>;
export class ApprovalReassignmentController {
  #state = initialApprovalReassignmentState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<ApprovalsUseCases, "reviewReassignment" | "reassign">,
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): ApprovalReassignmentState => this.#state;
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
    this.publish(initialApprovalReassignmentState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialApprovalReassignmentState);
    try {
      const result = await this.actions.reviewReassignment.execute(
        this.access,
        this.id,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish({
        ...initialApprovalReassignmentState,
        ...(result.ok
          ? ({ stage: "editing", request: result.value } as const)
          : ({ stage: "unavailable", failure: result.failure } as const)),
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialApprovalReassignmentState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (selection: ApprovalReassignmentSelection): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing" || !this.#state.request) return;
    const request = this.#state.request;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      request: Object.freeze({
        ...request,
        stages: Object.freeze(
          request.stages.map((stage) =>
            Object.freeze({ assignees: Object.freeze([...stage.assignees]) }),
          ),
        ),
        excludedAccountIds: Object.freeze([...request.excludedAccountIds]),
      }),
      selection: Object.freeze({
        reason: selection.reason,
        assignees: Object.freeze([...selection.assignees]),
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
        result = await this.actions.reassign.execute(
          this.access,
          submission.operation,
          submission.request,
          submission.selection,
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
          request: null,
          failure: null,
          operationId: null,
        });
      } else if (!wasUnconfirmed && approvalReassignmentWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          failure: result.failure,
          operationId: null,
          stage: ["stale_version", "approval_changed", "approval_not_found"].includes(
            result.failure.code,
          )
            ? "conflict"
            : "editing",
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: ApprovalReassignmentState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
