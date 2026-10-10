import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeavePolicyChange } from "../../domain/entities/leave-policy-change";
import { leavePolicySaveWasRejected } from "../../domain/policies/leave-policy-change-policy";
import { canManageLeavePolicies } from "../../domain/policies/leave-read-policy";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import {
  initialLeavePolicyEditorState,
  type LeavePolicyEditorState,
} from "../models/leave-policy-editor-state";

export type LeavePolicyFields = Omit<LeavePolicyChange, "id" | "expectedVersion">;
type Submission = Readonly<{ operation: OperationId; input: LeavePolicyChange }>;

export class LeavePolicyEditorController {
  #state = initialLeavePolicyEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<LeaveUseCases, "loadPolicy" | "savePolicy">,
    private readonly access: CompanyAccess,
    readonly creating: boolean,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): LeavePolicyEditorState => this.#state;
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
    this.publish(initialLeavePolicyEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialLeavePolicyEditorState);
    if (!canManageLeavePolicies(this.access.permissions)) {
      this.#pending = null;
      this.publish({
        ...initialLeavePolicyEditorState,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
      return;
    }
    if (this.creating) {
      this.#pending = null;
      this.publish({ ...initialLeavePolicyEditorState, stage: "editing" });
      return;
    }
    try {
      const result = await this.actions.loadPolicy.execute(
        this.access,
        this.id,
        null,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialLeavePolicyEditorState, stage: "editing", policy: result.value.current }
          : { ...initialLeavePolicyEditorState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialLeavePolicyEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: LeavePolicyFields): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing") return;
    const original = this.#state.policy;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        ...fields,
        id: original?.id ?? this.id,
        expectedVersion: original?.version ?? null,
        code: original?.code ?? fields.code,
        allowedContracts: Object.freeze([...fields.allowedContracts]),
        accrual: fields.accrual ? Object.freeze({ ...fields.accrual }) : null,
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
        result = await this.actions.savePolicy.execute(
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
        this.publish({ ...initialLeavePolicyEditorState, stage: "saved", receipt: result.value });
      } else if (!wasUnconfirmed && leavePolicySaveWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          failure: result.failure,
          operationId: null,
          stage: ["stale_version", "leave_type_code_immutable", "leave_type_not_found"].includes(
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
  private publish(state: LeavePolicyEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
