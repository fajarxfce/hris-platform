import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { type Failure, failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { CompensationChange } from "../../domain/entities/compensation-change";
import {
  compensationAccessLost,
  compensationSaveWasRejected,
} from "../../domain/policies/compensation-access-policy";
import { normalizeCompensation } from "../../domain/policies/compensation-validation";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  type CompensationEditorState,
  initialCompensationEditorState,
} from "../models/compensation-editor-state";

export type CompensationFields = Omit<
  CompensationChange,
  "expectedVersion" | "expectedEmploymentVersion"
>;
type Submission = Readonly<{ operation: OperationId; input: CompensationChange }>;
export class CompensationEditorController {
  #state = initialCompensationEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<PayrollUseCases, "loadCompensationEditor" | "saveCompensation">,
    private readonly access: CompanyAccess,
    private readonly employee: string,
    private readonly month: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): CompensationEditorState => this.#state;
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
    this.publish(initialCompensationEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialCompensationEditorState);
    try {
      const result = await this.actions.loadCompensationEditor.execute(
        this.access,
        this.employee,
        this.month,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialCompensationEditorState, stage: "editing", context: result.value }
          : { ...initialCompensationEditorState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialCompensationEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: CompensationFields): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing" || !this.#state.context) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: normalizeCompensation({
        ...fields,
        expectedVersion: this.#state.context.current?.version ?? null,
        expectedEmploymentVersion: this.#state.context.employee.employmentVersion,
      }),
    });
    await this.submit(false);
  };
  reportValidationFailure = (failure: Failure): void => {
    if (this.#active && this.#state.stage === "editing") this.publish({ ...this.#state, failure });
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
        result = await this.actions.saveCompensation.execute(
          this.access,
          this.employee,
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
        this.publish({ ...initialCompensationEditorState, stage: "saved", receipt: result.value });
      } else {
        const context =
          compensationAccessLost(result.failure) ||
          result.failure.code === "self_compensation_change_denied"
            ? null
            : this.#state.context;
        if (!wasUnconfirmed && compensationSaveWasRejected(result.failure)) {
          this.#submission = null;
          this.publish({
            ...this.#state,
            context,
            failure: result.failure,
            operationId: null,
            stage:
              context === null
                ? "unavailable"
                : ["stale_version", "stale_employment_version", "payroll_period_frozen"].includes(
                      result.failure.code,
                    )
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
  private publish(state: CompensationEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
