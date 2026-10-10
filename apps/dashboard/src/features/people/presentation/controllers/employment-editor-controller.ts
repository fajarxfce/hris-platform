import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AssignedEmploymentTerms } from "../../domain/entities/employment-assignments";
import type { EmploymentChange } from "../../domain/entities/employment-change";
import {
  canManageEmployment,
  employmentChangeWasRejected,
} from "../../domain/policies/employment-policy";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import {
  type EmploymentEditorState,
  initialEmploymentEditorState,
} from "../models/employment-editor-state";

export type EmploymentEditableFields = Readonly<{
  terms: Omit<AssignedEmploymentTerms, "startDate">;
  reason: string;
}>;
type Submission = Readonly<{ operation: OperationId; change: EmploymentChange }>;

export class EmploymentEditorController {
  #state = initialEmploymentEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<PeopleUseCases, "loadEmploymentDetails" | "reviseEmployment">,
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly asOf: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): EmploymentEditorState => this.#state;
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
    this.publish(initialEmploymentEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    this.#pending = null;
    this.publish(initialEmploymentEditorState);
    if (!canManageEmployment(this.access.permissions)) {
      this.publish({
        ...initialEmploymentEditorState,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
      return;
    }
    const pending = new AbortController();
    this.#pending = pending;
    try {
      const result = await this.actions.loadEmploymentDetails.execute(
        this.access,
        this.id,
        this.asOf,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialEmploymentEditorState, stage: "editing", details: result.value }
          : { ...initialEmploymentEditorState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialEmploymentEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: EmploymentEditableFields): Promise<void> => {
    const employee = this.#state.details?.employee;
    if (!this.#active || this.#state.stage !== "editing" || !employee) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      change: Object.freeze({
        employeeId: employee.id,
        expectedVersion: employee.version,
        terms: Object.freeze({ ...fields.terms, startDate: employee.terms.startDate }),
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
        result = await this.actions.reviseEmployment.execute(
          this.access,
          submission.operation,
          submission.change,
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
          savedDate: submission.change.terms.effectiveFrom,
          operationId: null,
          failure: null,
        });
      } else if (!wasUnconfirmed && employmentChangeWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: result.failure.code === "stale_version" ? "conflict" : "editing",
          failure: result.failure,
          operationId: null,
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: EmploymentEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
