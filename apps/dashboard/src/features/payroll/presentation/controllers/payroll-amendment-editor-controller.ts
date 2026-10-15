import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollAmendmentCorrection } from "../../domain/entities/payroll-amendment";
import type {
  PayrollAmendmentEditorTarget,
  PayrollAmendmentSave,
} from "../../domain/entities/payroll-amendment-preparation";
import {
  amendmentAccessLost,
  amendmentSaveWasRejected,
  amendmentSourceConflict,
  normalizeAmendmentTerms,
} from "../../domain/policies/payroll-amendment-authoring-policy";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialPayrollAmendmentEditorState,
  type PayrollAmendmentEditorState,
} from "../models/payroll-amendment-editor-state";

type Submission = Readonly<{ id: string; operation: OperationId; input: PayrollAmendmentSave }>;
export class PayrollAmendmentEditorController {
  #state = initialPayrollAmendmentEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      PayrollUseCases,
      "loadAmendmentEditor" | "previewAmendment" | "saveAmendment"
    >,
    private readonly access: CompanyAccess,
    private readonly target: PayrollAmendmentEditorTarget,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): PayrollAmendmentEditorState => this.#state;
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
    this.publish(initialPayrollAmendmentEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialPayrollAmendmentEditorState);
    try {
      const result = await this.actions.loadAmendmentEditor.execute(
        this.access,
        this.target,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? {
              ...initialPayrollAmendmentEditorState,
              stage: result.value.sourcesChanged ? "source-review" : "editing",
              context: result.value,
              terms:
                result.value.current?.terms ??
                Object.freeze({
                  sourceVersions: result.value.preparation.sourceVersions,
                  corrections: Object.freeze([]),
                }),
            }
          : {
              ...initialPayrollAmendmentEditorState,
              stage: "unavailable",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialPayrollAmendmentEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  acceptCurrentSources = (): void => {
    const { stage, context, terms } = this.#state;
    if (!this.#active || stage !== "source-review" || !context || !terms) return;
    this.publish({
      ...this.#state,
      stage: "editing",
      terms: Object.freeze({ ...terms, sourceVersions: context.preparation.sourceVersions }),
      dirty: true,
      preview: null,
      failure: null,
    });
  };
  openCorrection = (id: string): void => {
    const { stage, context } = this.#state;
    if (!this.#active || !["editing", "previewing"].includes(stage) || !context) return;
    const first = context.preparation.assessments.find(
        (row) => row.source.assessmentId === context.preparation.firstAssessmentId,
      ),
      selected = context.preparation.assessments.find((row) => row.source.assessmentId === id);
    if (!first || !selected || selected.month < first.month) return;
    this.#pending?.abort();
    this.#pending = null;
    this.publish({ ...this.#state, stage: "editing", selectedAssessment: id });
  };
  closeCorrection = (): void => {
    if (this.#active && this.#state.stage === "editing")
      this.publish({ ...this.#state, selectedAssessment: null });
  };
  applyCorrection = (correction: PayrollAmendmentCorrection): void => {
    const { stage, terms, selectedAssessment } = this.#state;
    if (
      !this.#active ||
      stage !== "editing" ||
      !terms ||
      correction.assessmentId !== selectedAssessment
    )
      return;
    this.publish({
      ...this.#state,
      terms: normalizeAmendmentTerms({
        ...terms,
        corrections: [
          ...terms.corrections.filter((row) => row.assessmentId !== selectedAssessment),
          correction,
        ],
      }),
      selectedAssessment: null,
      preview: null,
      dirty: true,
      failure: null,
    });
  };
  removeCorrection = (): void => {
    const { stage, terms, selectedAssessment } = this.#state;
    if (!this.#active || stage !== "editing" || !terms || selectedAssessment === null) return;
    this.publish({
      ...this.#state,
      terms: Object.freeze({
        ...terms,
        corrections: Object.freeze(
          terms.corrections.filter((row) => row.assessmentId !== selectedAssessment),
        ),
      }),
      selectedAssessment: null,
      preview: null,
      dirty: true,
      failure: null,
    });
  };
  preview = async (): Promise<void> => {
    const { stage, context, terms, selectedAssessment } = this.#state;
    if (!this.#active || stage !== "editing" || !context || !terms || selectedAssessment !== null)
      return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...this.#state, stage: "previewing", preview: null, failure: null });
    try {
      const result = await this.actions.previewAmendment.execute(
        this.access,
        context.preparation,
        terms,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) this.publish({ ...this.#state, stage: "editing", preview: result.value });
      else if (amendmentAccessLost(result.failure))
        this.publish({
          ...initialPayrollAmendmentEditorState,
          stage: "unavailable",
          failure: result.failure,
        });
      else
        this.publish({
          ...this.#state,
          stage: amendmentSourceConflict(result.failure) ? "conflict" : "editing",
          failure: result.failure,
        });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...this.#state,
          stage: "editing",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (reason: string): Promise<void> => {
    const { stage, context, terms, preview, selectedAssessment } = this.#state;
    if (
      !this.#active ||
      stage !== "editing" ||
      !context ||
      !terms ||
      !preview ||
      selectedAssessment !== null
    )
      return;
    try {
      this.#submission = Object.freeze({
        id: context.current?.summary.id ?? this.nextIdentifier(),
        operation: this.nextIdentifier() as OperationId,
        input: Object.freeze({
          ...normalizeAmendmentTerms(terms),
          firstAssessmentId: context.preparation.firstAssessmentId,
          expectedVersion: context.current?.summary.version ?? null,
          reason: reason.trim(),
        }),
      });
    } catch {
      this.publish({
        ...this.#state,
        failure: { code: "unexpected_error", fields: {}, parameters: {} },
      });
      return;
    }
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
        result = await this.actions.saveAmendment.execute(
          this.access,
          submission.id,
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
          ...initialPayrollAmendmentEditorState,
          stage: "saved",
          receipt: result.value,
        });
      } else {
        const cleared = amendmentAccessLost(result.failure),
          retained = cleared ? initialPayrollAmendmentEditorState : this.#state;
        if (!wasUnconfirmed && amendmentSaveWasRejected(result.failure)) {
          this.#submission = null;
          this.publish({
            ...retained,
            stage: cleared
              ? "unavailable"
              : amendmentSourceConflict(result.failure)
                ? "conflict"
                : "editing",
            preview: amendmentSourceConflict(result.failure) ? null : retained.preview,
            operationId: null,
            failure: result.failure,
          });
        } else
          this.publish({
            ...retained,
            stage: "unconfirmed",
            operationId: submission.operation,
            failure: result.failure,
          });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: PayrollAmendmentEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
