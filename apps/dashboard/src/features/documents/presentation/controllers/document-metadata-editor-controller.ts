import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { Document } from "../../domain/entities/document";
import type { DocumentMetadataFields } from "../../domain/entities/document-metadata";
import {
  documentMetadataAccessLost,
  documentMetadataReviewChanged,
  documentMetadataWasRejected,
} from "../../domain/policies/document-metadata-policy";
import type { DocumentMetadataUseCases } from "../contracts/document-metadata-use-cases";
import {
  type DocumentMetadataEditorState,
  initialDocumentMetadataEditorState,
} from "../models/document-metadata-editor-state";

type Submission = Readonly<{
  operation: OperationId;
  selection: Document;
  fields: DocumentMetadataFields;
}>;

export class DocumentMetadataEditorController {
  #state = initialDocumentMetadataEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<DocumentMetadataUseCases, "review" | "save">,
    private readonly access: CompanyAccess,
    private readonly documentId: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): DocumentMetadataEditorState => this.#state;
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
    this.publish(initialDocumentMetadataEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialDocumentMetadataEditorState);
    try {
      const result = await this.actions.review.execute(
        this.access,
        this.documentId,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialDocumentMetadataEditorState, stage: "editing", selection: result.value }
          : {
              ...initialDocumentMetadataEditorState,
              stage: "unavailable",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialDocumentMetadataEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: DocumentMetadataFields): Promise<void> => {
    const current = this.#state.selection;
    if (!this.#active || this.#state.stage !== "editing" || !current) return;
    try {
      this.#submission = Object.freeze({
        operation: this.nextIdentifier() as OperationId,
        selection: Object.freeze({
          ...current,
          metadata: current.metadata ? Object.freeze({ ...current.metadata }) : null,
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
        result = await this.actions.save.execute(
          this.access,
          submission.operation,
          submission.selection,
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
          ...initialDocumentMetadataEditorState,
          stage: "saved",
          receipt: result.value,
        });
      } else {
        const selection = documentMetadataAccessLost(result.failure) ? null : this.#state.selection;
        if (!wasUnconfirmed && documentMetadataWasRejected(result.failure)) {
          this.#submission = null;
          this.publish({
            ...this.#state,
            selection,
            failure: result.failure,
            operationId: null,
            stage: !selection
              ? "unavailable"
              : documentMetadataReviewChanged(result.failure)
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
  private publish(state: DocumentMetadataEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
