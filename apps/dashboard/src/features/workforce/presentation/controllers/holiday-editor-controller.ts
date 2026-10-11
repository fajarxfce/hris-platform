import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { HolidayChange } from "../../domain/entities/holiday-change";
import { holidaySaveWasRejected } from "../../domain/policies/holiday-change-policy";
import { canManageHolidays } from "../../domain/policies/holiday-policy";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { type HolidayEditorState, initialHolidayEditorState } from "../models/holiday-editor-state";

export type HolidayEditableFields = Omit<HolidayChange, "id" | "expectedVersion">;
type Submission = Readonly<{ operation: OperationId; change: HolidayChange }>;

/** Owns one observed revision and one immutable submission until its receipt is known. */
export class HolidayEditorController {
  #state = initialHolidayEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  readonly id: string;
  constructor(
    private readonly actions: Pick<WorkforceUseCases, "loadHoliday" | "saveHoliday">,
    private readonly access: CompanyAccess,
    readonly creating: boolean,
    id: string,
    private readonly nextIdentifier: () => string,
  ) {
    this.id = id;
  }
  getSnapshot = (): HolidayEditorState => this.#state;
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
    this.publish(initialHolidayEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission !== null) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialHolidayEditorState);
    if (!canManageHolidays(this.access.permissions)) {
      this.#pending = null;
      this.publish({
        ...initialHolidayEditorState,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
      return;
    }
    if (this.creating) {
      this.#pending = null;
      this.publish({ ...initialHolidayEditorState, stage: "editing" });
      return;
    }
    try {
      const result = await this.actions.loadHoliday.execute(this.access, this.id, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish({
        ...initialHolidayEditorState,
        ...(result.ok
          ? ({ stage: "editing", holiday: result.value } as const)
          : ({ stage: "unavailable", failure: result.failure } as const)),
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialHolidayEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: HolidayEditableFields): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing") return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      change: Object.freeze({
        ...fields,
        id: this.id,
        expectedVersion: this.#state.holiday?.version ?? null,
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
        result = await this.actions.saveHoliday.execute(
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
      } else if (!wasUnconfirmed && holidaySaveWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: ["stale_version", "holiday_not_found"].includes(result.failure.code)
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
  private publish(state: HolidayEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
