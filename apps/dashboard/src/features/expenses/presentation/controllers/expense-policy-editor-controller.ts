import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpensePolicyChange } from "../../domain/entities/expense-policy-change";
import { expensePolicySaveWasRejected } from "../../domain/policies/expense-policy-change-policy";
import { canManageExpensePolicies } from "../../domain/policies/expense-read-policy";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import {
  type ExpensePolicyEditorState,
  initialExpensePolicyEditorState,
} from "../models/expense-policy-editor-state";

export type ExpensePolicyFields = Omit<ExpensePolicyChange, "id" | "expectedVersion">;
type Submission = Readonly<{ operation: OperationId; input: ExpensePolicyChange }>;

export class ExpensePolicyEditorController {
  #state = initialExpensePolicyEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<ExpenseUseCases, "loadPolicy" | "savePolicy">,
    private readonly access: CompanyAccess,
    readonly creating: boolean,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): ExpensePolicyEditorState => this.#state;
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
    this.publish(initialExpensePolicyEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialExpensePolicyEditorState);
    if (!canManageExpensePolicies(this.access.permissions)) {
      this.#pending = null;
      this.publish({
        ...initialExpensePolicyEditorState,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
      return;
    }
    if (this.creating) {
      this.#pending = null;
      this.publish({ ...initialExpensePolicyEditorState, stage: "editing" });
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
          ? { ...initialExpensePolicyEditorState, stage: "editing", policy: result.value.current }
          : { ...initialExpensePolicyEditorState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialExpensePolicyEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: ExpensePolicyFields): Promise<void> => {
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
        this.publish({ ...initialExpensePolicyEditorState, stage: "saved", receipt: result.value });
      } else if (!wasUnconfirmed && expensePolicySaveWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          failure: result.failure,
          operationId: null,
          stage: [
            "stale_version",
            "expense_category_code_immutable",
            "expense_category_not_found",
          ].includes(result.failure.code)
            ? "conflict"
            : "editing",
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: ExpensePolicyEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
