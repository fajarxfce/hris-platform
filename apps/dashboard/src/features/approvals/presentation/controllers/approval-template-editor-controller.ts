import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ApprovalTemplateChange } from "../../domain/entities/approval-template-change";
import { canManageApprovals } from "../../domain/policies/approval-read-policy";
import { approvalTemplateSaveWasRejected } from "../../domain/policies/approval-template-policy";
import type { ApprovalsUseCases } from "../contracts/approvals-use-cases";
import {
  type ApprovalTemplateEditorState,
  initialApprovalTemplateEditorState,
} from "../models/approval-template-editor-state";

export type ApprovalTemplateFields = Omit<ApprovalTemplateChange, "id" | "expectedVersion">;
type Submission = Readonly<{ operation: OperationId; input: ApprovalTemplateChange }>;

export class ApprovalTemplateEditorController {
  #state = initialApprovalTemplateEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<ApprovalsUseCases, "loadTemplate" | "saveTemplate">,
    private readonly access: CompanyAccess,
    readonly creating: boolean,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): ApprovalTemplateEditorState => this.#state;
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
    this.publish(initialApprovalTemplateEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialApprovalTemplateEditorState);
    if (!canManageApprovals(this.access.permissions)) {
      this.#pending = null;
      this.publish({
        ...initialApprovalTemplateEditorState,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
      return;
    }
    if (this.creating) {
      this.#pending = null;
      this.publish({ ...initialApprovalTemplateEditorState, stage: "editing" });
      return;
    }
    try {
      const result = await this.actions.loadTemplate.execute(
        this.access,
        this.id,
        null,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish({
        ...initialApprovalTemplateEditorState,
        ...(result.ok
          ? ({ stage: "editing", template: result.value } as const)
          : ({ stage: "unavailable", failure: result.failure } as const)),
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialApprovalTemplateEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: ApprovalTemplateFields): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing") return;
    const original = this.#state.template;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        id: original?.id ?? this.id,
        expectedVersion: original?.version ?? null,
        kind: original?.kind ?? fields.kind,
        name: fields.name,
        active: fields.active,
        reason: fields.reason,
        effectiveFrom: fields.effectiveFrom,
        category: fields.category,
        minimumAmount: fields.minimumAmount,
        stages: Object.freeze(
          fields.stages.map((stage) =>
            Object.freeze({
              assignment: stage.assignment,
              accountIds: Object.freeze([...stage.accountIds]),
              permission: stage.permission,
            }),
          ),
        ),
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
        result = await this.actions.saveTemplate.execute(
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
          ...this.#state,
          stage: "saved",
          receipt: result.value,
          template: null,
          failure: null,
          operationId: null,
        });
      } else if (!wasUnconfirmed && approvalTemplateSaveWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          failure: result.failure,
          operationId: null,
          stage: [
            "stale_version",
            "approval_kind_immutable",
            "approval_template_not_found",
          ].includes(result.failure.code)
            ? "conflict"
            : "editing",
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: ApprovalTemplateEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
