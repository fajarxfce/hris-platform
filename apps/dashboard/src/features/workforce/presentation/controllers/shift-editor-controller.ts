import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ShiftChange } from "../../domain/entities/shift-change";
import { shiftSaveWasRejected } from "../../domain/policies/shift-change-policy";
import { canManageShifts } from "../../domain/policies/shift-policy";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { initialShiftEditorState, type ShiftEditorState } from "../models/shift-editor-state";

export type ShiftEditableFields = Omit<ShiftChange, "id" | "expectedVersion">;
type Submission = Readonly<{ operation: OperationId; change: ShiftChange }>;

/** Owns one observed revision and one immutable submission until its receipt is known. */
export class ShiftEditorController {
  #state = initialShiftEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  readonly id: string;
  constructor(
    private readonly actions: Pick<WorkforceUseCases, "loadShift" | "saveShift">,
    private readonly access: CompanyAccess,
    readonly creating: boolean,
    id: string,
    private readonly nextIdentifier: () => string,
  ) {
    this.id = id;
  }
  getSnapshot = (): ShiftEditorState => this.#state;
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
    this.publish(initialShiftEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission !== null) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialShiftEditorState);
    if (!canManageShifts(this.access.permissions)) {
      this.#pending = null;
      this.publish({
        ...initialShiftEditorState,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
      return;
    }
    if (this.creating) {
      this.#pending = null;
      this.publish({ ...initialShiftEditorState, stage: "editing" });
      return;
    }
    try {
      const result = await this.actions.loadShift.execute(this.access, this.id, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish({
        ...initialShiftEditorState,
        ...(result.ok
          ? ({ stage: "editing", shift: result.value } as const)
          : ({ stage: "unavailable", failure: result.failure } as const)),
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialShiftEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: ShiftEditableFields): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing") return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      change: Object.freeze({
        ...fields,
        details: Object.freeze({
          ...fields.details,
          fence: fields.details.fence ? Object.freeze({ ...fields.details.fence }) : null,
        }),
        id: this.id,
        expectedVersion: this.#state.shift?.version ?? null,
      }),
    });
    await this.submit(false);
  };
  retrySave = async (): Promise<void> => {
    if (!this.#active || this.#state.stage !== "unconfirmed") return;
    await this.submit(true);
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
        result = await this.actions.saveShift.execute(
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
          failure: null,
          receipt: result.value,
          operationId: null,
        });
      } else if (!wasUnconfirmed && shiftSaveWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: ["stale_version", "shift_not_found"].includes(result.failure.code)
            ? "conflict"
            : "editing",
          failure: result.failure,
          operationId: null,
        });
      } else {
        this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: ShiftEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
