import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  DocumentRetentionPolicyFields,
  DocumentRetentionPolicySelection,
} from "../../domain/entities/document-retention-policy";
import {
  documentRetentionAccessLost,
  documentRetentionReviewChanged,
  documentRetentionWasRejected,
} from "../../domain/policies/document-retention-failure";
import type { DocumentRetentionUseCases } from "../contracts/document-retention-use-cases";
import {
  type DocumentRetentionEditorState,
  initialDocumentRetentionEditorState,
} from "../models/document-retention-editor-state";

type Submission = Readonly<{
  operation: OperationId;
  policyId: string;
  selection: DocumentRetentionPolicySelection;
  fields: DocumentRetentionPolicyFields;
}>;

export class DocumentRetentionEditorController {
  #state = initialDocumentRetentionEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<DocumentRetentionUseCases, "reviewPolicy" | "savePolicy">,
    private readonly access: CompanyAccess,
    private readonly classification: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): DocumentRetentionEditorState => this.#state;
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
    this.publish(initialDocumentRetentionEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialDocumentRetentionEditorState);
    try {
      const result = await this.actions.reviewPolicy.execute(
        this.access,
        this.classification,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialDocumentRetentionEditorState, stage: "editing", selection: result.value }
          : {
              ...initialDocumentRetentionEditorState,
              stage: "unavailable",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialDocumentRetentionEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: DocumentRetentionPolicyFields): Promise<void> => {
    const current = this.#state.selection;
    if (!this.#active || this.#state.stage !== "editing" || !current) return;
    try {
      this.#submission = Object.freeze({
        operation: this.nextIdentifier() as OperationId,
        policyId: current.policy?.id ?? this.nextIdentifier(),
        selection: Object.freeze({
          ...current,
          policy: current.policy ? Object.freeze({ ...current.policy }) : null,
        }),
        fields: Object.freeze({ ...fields }),
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
  retry = async (): Promise<void> => {
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
          submission.selection,
          submission.policyId,
          submission.fields,
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
          ...initialDocumentRetentionEditorState,
          stage: "saved",
          receipt: result.value,
        });
      } else {
        const selection = documentRetentionAccessLost(result.failure)
          ? null
          : this.#state.selection;
        if (!wasUnconfirmed && documentRetentionWasRejected(result.failure)) {
          this.#submission = null;
          this.publish({
            ...this.#state,
            selection,
            failure: result.failure,
            operationId: null,
            stage: !selection
              ? "unavailable"
              : documentRetentionReviewChanged(result.failure)
                ? "conflict"
                : "editing",
          });
        } else
          this.publish({
            ...this.#state,
            selection,
            stage: "unconfirmed",
            failure: result.failure,
          });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: DocumentRetentionEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
