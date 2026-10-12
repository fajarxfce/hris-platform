import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  DocumentRetention,
  DocumentRetentionIntent,
} from "../../domain/entities/document-retention";
import {
  documentRetentionAccessLost,
  documentRetentionReviewChanged,
  documentRetentionWasRejected,
} from "../../domain/policies/document-retention-failure";
import type { DocumentRetentionUseCases } from "../contracts/document-retention-use-cases";
import {
  type DocumentRetentionChangeState,
  initialDocumentRetentionChangeState,
} from "../models/document-retention-change-state";

type Submission = Readonly<{ operation: OperationId; review: DocumentRetention; reason: string }>;
export class DocumentRetentionChangeController {
  #state = initialDocumentRetentionChangeState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<DocumentRetentionUseCases, "reviewChange" | "change">,
    private readonly access: CompanyAccess,
    private readonly documentId: string,
    readonly intent: DocumentRetentionIntent,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): DocumentRetentionChangeState => this.#state;
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
    this.publish(initialDocumentRetentionChangeState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialDocumentRetentionChangeState);
    try {
      const result = await this.actions.reviewChange.execute(
        this.access,
        this.documentId,
        this.intent,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialDocumentRetentionChangeState, stage: "reviewing", review: result.value }
          : {
              ...initialDocumentRetentionChangeState,
              stage: "unavailable",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialDocumentRetentionChangeState,
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
    try {
      this.#submission = Object.freeze({
        operation: this.nextIdentifier() as OperationId,
        review: Object.freeze({
          ...current,
          archive: current.archive ? Object.freeze({ ...current.archive }) : null,
        }),
        reason,
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
      stage: "submitting",
      failure: null,
      operationId: submission.operation,
    });
    try {
      let result: Result<MutationReceipt>;
      try {
        result = await this.actions.change.execute(
          this.access,
          submission.operation,
          submission.review,
          this.intent,
          submission.reason,
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
          ...initialDocumentRetentionChangeState,
          stage: "saved",
          receipt: result.value,
        });
      } else {
        const review = documentRetentionAccessLost(result.failure) ? null : this.#state.review;
        if (!wasUnconfirmed && documentRetentionWasRejected(result.failure)) {
          this.#submission = null;
          this.publish({
            ...this.#state,
            review,
            failure: result.failure,
            operationId: null,
            stage: !review
              ? "unavailable"
              : documentRetentionReviewChanged(result.failure)
                ? "conflict"
                : "reviewing",
          });
        } else
          this.publish({ ...this.#state, review, stage: "unconfirmed", failure: result.failure });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: DocumentRetentionChangeState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
