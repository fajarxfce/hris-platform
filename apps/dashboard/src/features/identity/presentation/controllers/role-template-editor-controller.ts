import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { RoleTemplateChange } from "../../domain/entities/role-template-change";
import type { CompanyAccess } from "../../domain/entities/session";
import { roleTemplateSaveWasRejected } from "../../domain/policies/role-template-policy";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import {
  initialRoleTemplateEditorState,
  type RoleTemplateEditorState,
} from "../models/role-template-editor-state";

export type RoleTemplateFields = Omit<RoleTemplateChange, "id" | "expectedVersion">;
type Submission = Readonly<{ operation: OperationId; input: RoleTemplateChange }>;
export class RoleTemplateEditorController {
  #state = initialRoleTemplateEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      IdentityAdministrationUseCases,
      "loadRoleTemplate" | "loadAssignablePermissions" | "saveRoleTemplate"
    >,
    private readonly access: CompanyAccess,
    readonly creating: boolean,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): RoleTemplateEditorState => this.#state;
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
    this.publish(initialRoleTemplateEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialRoleTemplateEditorState);
    try {
      const [permissions, review] = await Promise.all([
        this.actions.loadAssignablePermissions.execute(this.access, pending.signal),
        this.creating
          ? Promise.resolve(success(null))
          : this.actions.loadRoleTemplate.execute(this.access, this.id, null, pending.signal),
      ]);
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (!permissions.ok || !review.ok) {
        this.publish({
          ...initialRoleTemplateEditorState,
          stage: "unavailable",
          failure: !permissions.ok ? permissions.failure : !review.ok ? review.failure : null,
        });
        return;
      }
      this.publish({
        ...initialRoleTemplateEditorState,
        stage: "editing",
        template: review.value?.current ?? null,
        permissions: permissions.value,
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending) {
        pending.abort();
        this.publish({
          ...initialRoleTemplateEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: RoleTemplateFields): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing") return;
    const original = this.#state.template;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        ...fields,
        id: original?.id ?? this.id,
        expectedVersion: original?.version ?? null,
        code: original?.code ?? fields.code,
        permissions: Object.freeze([...fields.permissions]),
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
        result = await this.actions.saveRoleTemplate.execute(
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
        this.publish({ ...initialRoleTemplateEditorState, stage: "saved", receipt: result.value });
      } else if (!wasUnconfirmed && roleTemplateSaveWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          failure: result.failure,
          operationId: null,
          stage: ["stale_version", "role_code_immutable", "role_template_not_found"].includes(
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
  private publish(state: RoleTemplateEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
