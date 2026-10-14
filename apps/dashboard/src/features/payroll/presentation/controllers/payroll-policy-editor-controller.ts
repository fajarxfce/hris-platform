import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPolicyChange } from "../../domain/entities/payroll-policy-change";
import {
  payrollPolicyAccessLost,
  payrollPolicySaveWasRejected,
} from "../../domain/policies/payroll-access-policy";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialPayrollPolicyEditorState,
  type PayrollPolicyEditorState,
} from "../models/payroll-policy-editor-state";

export type PayrollPolicyFields = Omit<PayrollPolicyChange, "expectedVersion">;
type Submission = Readonly<{ operation: OperationId; input: PayrollPolicyChange }>;
export class PayrollPolicyEditorController {
  #state = initialPayrollPolicyEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<PayrollUseCases, "loadPolicyEditor" | "savePolicy">,
    private readonly access: CompanyAccess,
    private readonly month: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): PayrollPolicyEditorState => this.#state;
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
    this.publish(initialPayrollPolicyEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialPayrollPolicyEditorState);
    try {
      const result = await this.actions.loadPolicyEditor.execute(
        this.access,
        this.month,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialPayrollPolicyEditorState, stage: "editing", context: result.value }
          : { ...initialPayrollPolicyEditorState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialPayrollPolicyEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: PayrollPolicyFields): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing" || !this.#state.context) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        ...fields,
        expectedVersion: this.#state.context.current?.version ?? null,
        reviewReferences: Object.freeze(fields.reviewReferences.slice(0, 11)),
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
        this.publish({ ...initialPayrollPolicyEditorState, stage: "saved", receipt: result.value });
      } else {
        const context = payrollPolicyAccessLost(result.failure) ? null : this.#state.context;
        if (!wasUnconfirmed && payrollPolicySaveWasRejected(result.failure)) {
          this.#submission = null;
          this.publish({
            ...this.#state,
            context,
            failure: result.failure,
            operationId: null,
            stage:
              context === null
                ? "unavailable"
                : ["stale_version", "payroll_period_frozen"].includes(result.failure.code)
                  ? "conflict"
                  : "editing",
          });
        } else
          this.publish({ ...this.#state, context, stage: "unconfirmed", failure: result.failure });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: PayrollPolicyEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
