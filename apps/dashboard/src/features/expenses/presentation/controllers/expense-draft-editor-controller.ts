import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { type Failure, failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseDraftChange } from "../../domain/entities/expense-draft-change";
import type { ExpenseDraftEmployee } from "../../domain/entities/expense-draft-employee";
import type { ExpenseDraftReferences } from "../../domain/entities/expense-draft-references";
import type { ExpenseLine } from "../../domain/entities/expense-line";
import {
  canManageExpenseDrafts,
  expenseDraftAccessLost,
  expenseDraftSaveWasRejected,
  normalizeExpenseDraft,
  normalizeExpenseLine,
  validateExpenseDraft,
  validateExpenseLine,
} from "../../domain/policies/expense-draft-policy";
import type { ExpenseUseCases } from "../contracts/expense-use-cases";
import {
  type ExpenseDraftEditorState,
  initialExpenseDraftEditorState,
} from "../models/expense-draft-editor-state";
import { mergeExpenseDraftLabels } from "../models/expense-draft-labels";
export type ExpenseDraftFields = Pick<ExpenseDraftChange, "title" | "description" | "reason">;
type Command = Readonly<{ operation: OperationId; input: ExpenseDraftChange }>;

export class ExpenseDraftEditorController {
  #state = initialExpenseDraftEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #command: Command | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<ExpenseUseCases, "reviewDraftEdit" | "saveDraft">,
    private readonly access: CompanyAccess,
    private readonly creating: boolean,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): ExpenseDraftEditorState => this.#state;
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
    this.#command = null;
    this.publish(initialExpenseDraftEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#command) return;
    this.#pending?.abort();
    this.publish(initialExpenseDraftEditorState);
    if (!canManageExpenseDrafts(this.access.permissions)) {
      this.#pending = null;
      this.publish({
        ...initialExpenseDraftEditorState,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
      return;
    }
    if (this.creating) {
      this.#pending = null;
      this.publish({ ...initialExpenseDraftEditorState, stage: "choosing" });
      return;
    }
    const pending = new AbortController();
    this.#pending = pending;
    try {
      const result = await this.actions.reviewDraftEdit.execute(
        this.access,
        this.id,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (!result.ok) {
        this.publish({
          ...initialExpenseDraftEditorState,
          stage: "unavailable",
          failure: result.failure,
        });
        return;
      }
      const { claim, references } = result.value;
      this.publish({
        ...initialExpenseDraftEditorState,
        stage: "editing",
        original: result.value,
        references,
        employee: {
          id: claim.employmentId,
          employeeNumber: claim.draft.employeeNumber,
          legalName: claim.draft.employeeName,
        },
        lines: claim.draft.lines,
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialExpenseDraftEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  chooseEmployee = (employee: ExpenseDraftEmployee, companyDate: string): void => {
    if (
      !this.#active ||
      this.#state.stage !== "choosing" ||
      employee.companyId !== this.access.companyId
    )
      return;
    this.publish({
      ...initialExpenseDraftEditorState,
      stage: "editing",
      companyDate,
      changed: true,
      employee: {
        id: employee.id,
        employeeNumber: employee.employeeNumber,
        legalName: employee.legalName,
      },
      references: Object.freeze({
        companyId: this.access.companyId,
        employmentId: employee.id,
        categories: [],
        costCenters: [],
        receipts: [],
      }),
    });
  };
  addLine = (today: string): void => {
    if (
      !this.#active ||
      this.#state.stage !== "editing" ||
      this.#state.panel.kind !== "closed" ||
      this.#state.lines.length >= 20
    )
      return;
    const line = Object.freeze({
      id: this.nextIdentifier(),
      categoryId: "",
      occurredOn: this.#state.companyDate ?? today,
      amount: "",
      description: "",
      costCenterId: null,
      receiptRevisionIds: Object.freeze([]),
    });
    this.publish({
      ...this.#state,
      panel: { kind: "line", creating: true, line, dirty: false },
      entryFailure: null,
    });
  };
  editLine = (id: string): void => {
    if (!this.#active || this.#state.stage !== "editing" || this.#state.panel.kind !== "closed")
      return;
    const line = this.#state.lines.find((item) => item.id === id);
    if (!line) return;
    this.publish({
      ...this.#state,
      panel: { kind: "line", creating: false, line, dirty: false },
      entryFailure: null,
    });
  };
  setLineDirty = (id: string, dirty: boolean): void => {
    const panel = this.#state.panel;
    if (
      !this.#active ||
      this.#state.stage !== "editing" ||
      panel.kind !== "line" ||
      panel.line.id !== id ||
      panel.dirty === dirty
    )
      return;
    this.publish({ ...this.#state, panel: { ...panel, dirty } });
  };
  closeLine = (): void => {
    if (!this.#active || this.#state.stage !== "editing") return;
    this.publish({ ...this.#state, panel: { kind: "closed" }, entryFailure: null });
  };
  saveLine = (input: ExpenseLine, labels: ExpenseDraftReferences): void => {
    const panel = this.#state.panel,
      references = this.#state.references;
    if (
      !this.#active ||
      this.#state.stage !== "editing" ||
      panel.kind !== "line" ||
      panel.line.id !== input.id ||
      !references ||
      labels.companyId !== this.access.companyId ||
      labels.employmentId !== references.employmentId
    )
      return;
    const line = normalizeExpenseLine(input),
      failure = validateExpenseLine(line);
    if (failure) {
      this.publish({ ...this.#state, entryFailure: failure });
      return;
    }
    const lines = Object.freeze(
      panel.creating
        ? [...this.#state.lines, line]
        : this.#state.lines.map((item) => (item.id === line.id ? line : item)),
    );
    this.publish({
      ...this.#state,
      lines,
      references: mergeExpenseDraftLabels(references, labels, lines),
      changed: true,
      panel: { kind: "closed" },
      entryFailure: null,
      failure: null,
    });
  };
  removeLine = (): void => {
    const panel = this.#state.panel,
      references = this.#state.references;
    if (!this.#active || this.#state.stage !== "editing" || panel.kind !== "line" || !references)
      return;
    const lines = Object.freeze(this.#state.lines.filter((item) => item.id !== panel.line.id));
    this.publish({
      ...this.#state,
      lines,
      references: mergeExpenseDraftLabels(references, references, lines),
      changed: true,
      panel: { kind: "closed" },
      entryFailure: null,
      failure: null,
    });
  };
  withdrawEvidence = (failure: Failure): void => {
    if (
      !this.#active ||
      !["editing", "choosing"].includes(this.#state.stage) ||
      !expenseDraftAccessLost(failure)
    )
      return;
    this.publish({ ...initialExpenseDraftEditorState, stage: "unavailable", failure });
  };
  save = async (fields: ExpenseDraftFields): Promise<void> => {
    if (
      !this.#active ||
      this.#state.stage !== "editing" ||
      this.#state.panel.kind !== "closed" ||
      !this.#state.employee
    )
      return;
    const input = normalizeExpenseDraft({
        ...fields,
        id: this.id,
        companyId: this.access.companyId,
        employmentId: this.#state.employee.id,
        expectedVersion: this.#state.original?.claim.version ?? null,
        lines: this.#state.lines,
      }),
      failure = validateExpenseDraft(input);
    if (failure) {
      this.publish({ ...this.#state, failure });
      return;
    }
    this.#command = Object.freeze({ operation: this.nextIdentifier() as OperationId, input });
    await this.send(false);
  };
  retry = async (): Promise<void> => {
    if (this.#active && this.#state.stage === "unconfirmed") await this.send(true);
  };
  private async send(wasUnconfirmed: boolean): Promise<void> {
    const command = this.#command;
    if (!command) return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({
      ...this.#state,
      stage: "saving",
      operationId: command.operation,
      failure: null,
    });
    try {
      let result: Result<MutationReceipt>;
      try {
        result = await this.actions.saveDraft.execute(
          this.access,
          command.operation,
          command.input,
          pending.signal,
        );
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#command = null;
        this.publish({ ...initialExpenseDraftEditorState, stage: "saved", receipt: result.value });
        return;
      }
      const unavailable = expenseDraftAccessLost(result.failure);
      const retained = unavailable
        ? { ...initialExpenseDraftEditorState, operationId: command.operation }
        : this.#state;
      if (!wasUnconfirmed && expenseDraftSaveWasRejected(result.failure)) {
        this.#command = null;
        this.publish({
          ...retained,
          operationId: null,
          failure: result.failure,
          stage: unavailable
            ? "unavailable"
            : [
                  "stale_version",
                  "expense_not_editable",
                  "expense_identity_immutable",
                  "expense_draft_revision_limit",
                ].includes(result.failure.code)
              ? "conflict"
              : "editing",
        });
      } else this.publish({ ...retained, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: ExpenseDraftEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
