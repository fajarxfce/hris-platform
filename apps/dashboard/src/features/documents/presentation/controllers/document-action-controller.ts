import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  DocumentRevisionIntent,
  DocumentRevisionSnapshot,
} from "../../domain/entities/document-revision-action";
import {
  documentActionAccessLost,
  documentActionReviewChanged,
  documentActionWasRejected,
} from "../../domain/policies/document-action-policy";
import type { DocumentsUseCases } from "../contracts/documents-use-cases";
import {
  type DocumentActionState,
  initialDocumentActionState,
} from "../models/document-action-state";

type Submission = Readonly<{
  operation: OperationId;
  perform: (signal: AbortSignal) => Promise<Result<MutationReceipt>>;
}>;

export class DocumentActionController {
  #state = initialDocumentActionState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<DocumentsUseCases, "reviewAction" | "validate" | "cancelUpload">,
    private readonly access: CompanyAccess,
    private readonly documentId: string,
    private readonly revisionId: string,
    readonly intent: DocumentRevisionIntent,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): DocumentActionState => this.#state;
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
    this.publish(initialDocumentActionState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialDocumentActionState);
    try {
      const result = await this.actions.reviewAction.execute(
        this.access,
        this.documentId,
        this.revisionId,
        this.intent,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialDocumentActionState, stage: "reviewing", review: result.value }
          : { ...initialDocumentActionState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialDocumentActionState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  confirm = async (reason: string): Promise<void> => {
    const current = this.#state.review;
    if (!this.#active || this.#state.stage !== "reviewing" || !current) return;
    let operation: OperationId;
    try {
      operation = this.nextIdentifier() as OperationId;
    } catch {
      this.publish({
        ...this.#state,
        failure: { code: "unexpected_error", fields: {}, parameters: {} },
      });
      return;
    }
    const review: DocumentRevisionSnapshot = Object.freeze({
      id: current.id,
      documentId: current.documentId,
      companyId: current.companyId,
      version: current.version,
      availableActions: Object.freeze([...current.availableActions]),
    });
    const action = this.intent === "validate" ? this.actions.validate : this.actions.cancelUpload;
    this.#submission = Object.freeze({
      operation,
      perform: (signal: AbortSignal) =>
        action.execute(this.access, operation, review, reason, signal),
    });
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
      stage: "submitting",
      failure: null,
      operationId: submission.operation,
    });
    try {
      let result: Result<MutationReceipt>;
      try {
        result = await submission.perform(pending.signal);
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
          review: null,
          failure: null,
          operationId: null,
          receipt: result.value,
        });
      } else {
        const review = documentActionAccessLost(result.failure) ? null : this.#state.review;
        if (!wasUnconfirmed && documentActionWasRejected(result.failure)) {
          this.#submission = null;
          this.publish({
            ...this.#state,
            review,
            failure: result.failure,
            operationId: null,
            stage: !review
              ? "unavailable"
              : documentActionReviewChanged(result.failure)
                ? "conflict"
                : "reviewing",
          });
        } else {
          this.publish({ ...this.#state, review, stage: "unconfirmed", failure: result.failure });
        }
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: DocumentActionState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
