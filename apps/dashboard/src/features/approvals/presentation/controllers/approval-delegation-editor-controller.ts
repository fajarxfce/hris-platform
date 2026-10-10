import type { AccountId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalDelegationChange } from "../../domain/entities/approval-delegation-change";
import { approvalDelegationSaveWasRejected } from "../../domain/policies/approval-delegation-policy";
import { canReadApprovalInbox } from "../../domain/policies/approval-read-policy";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import {
  type ApprovalDelegationEditorState,
  initialApprovalDelegationEditorState,
} from "../models/approval-delegation-editor-state";

export type ApprovalDelegationFields = Omit<ApprovalDelegationChange, "id" | "expectedVersion">;
type Submission = Readonly<{ operation: OperationId; input: ApprovalDelegationChange }>;

export class ApprovalDelegationEditorController {
  #state = initialApprovalDelegationEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<ApprovalsUseCases, "loadDelegationForEdit" | "saveDelegation">,
    private readonly access: CompanyAccess,
    private readonly account: AccountId,
    readonly creating: boolean,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): ApprovalDelegationEditorState => this.#state;
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
    this.publish(initialApprovalDelegationEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialApprovalDelegationEditorState);
    if (!canReadApprovalInbox(this.access.permissions)) {
      this.#pending = null;
      this.publish({
        ...initialApprovalDelegationEditorState,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
      return;
    }
    if (this.creating) {
      this.#pending = null;
      this.publish({ ...initialApprovalDelegationEditorState, stage: "editing" });
      return;
    }
    try {
      const result = await this.actions.loadDelegationForEdit.execute(
        this.access,
        this.account,
        this.id,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish({
        ...initialApprovalDelegationEditorState,
        ...(result.ok
          ? ({ stage: "editing", delegation: result.value } as const)
          : ({ stage: "unavailable", failure: result.failure } as const)),
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialApprovalDelegationEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: ApprovalDelegationFields): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing") return;
    const original = this.#state.delegation;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        id: original?.id ?? this.id,
        expectedVersion: original?.version ?? null,
        kind: fields.kind,
        fromAccount: original?.fromAccount ?? fields.fromAccount,
        toAccount: fields.toAccount,
        validFrom: fields.validFrom,
        validUntil: fields.validUntil,
        active: fields.active,
        reason: fields.reason,
      }),
    });
    await this.submit(false);
  };
  retrySave = async (): Promise<void> => {
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
        result = await this.actions.saveDelegation.execute(
          this.access,
          this.account,
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
          ...this.#state,
          stage: "saved",
          receipt: result.value,
          delegation: null,
          failure: null,
          operationId: null,
        });
      } else if (!wasUnconfirmed && approvalDelegationSaveWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          failure: result.failure,
          operationId: null,
          stage: ["stale_version", "delegator_immutable", "approval_delegation_not_found"].includes(
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
  private publish(state: ApprovalDelegationEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
