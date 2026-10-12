import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentRetirementReview } from "../../domain/entities/document-retirement";
import {
  documentRetentionAccessLost,
  documentRetentionReviewChanged,
  documentRetentionWasRejected,
} from "../../domain/policies/document-retention-failure";
import type { DocumentRetentionUseCases } from "../contracts/document-retention-use-cases";
import {
  type DocumentRetirementState,
  initialDocumentRetirementState,
} from "../models/document-retirement-state";

type Submission = Readonly<{
  operation: OperationId;
  review: DocumentRetirementReview;
  reason: string;
}>;
export class DocumentRetirementController {
  #state = initialDocumentRetirementState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      DocumentRetentionUseCases,
      "reviewRetirement" | "retireRevision"
    >,
    private readonly access: CompanyAccess,
    private readonly documentId: string,
    private readonly revisionId: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): DocumentRetirementState => this.#state;
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
    this.publish(initialDocumentRetirementState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialDocumentRetirementState);
    try {
      const result = await this.actions.reviewRetirement.execute(
        this.access,
        this.documentId,
        this.revisionId,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? {
              ...initialDocumentRetirementState,
              stage: result.value.blockingReason === null ? "reviewing" : "blocked",
              review: result.value,
            }
          : { ...initialDocumentRetirementState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialDocumentRetirementState,
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
          retention: Object.freeze({
            ...current.retention,
            archive: current.retention.archive
              ? Object.freeze({ ...current.retention.archive })
              : null,
          }),
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
        result = await this.actions.retireRevision.execute(
          this.access,
          submission.operation,
          submission.review,
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
        this.publish({ ...initialDocumentRetirementState, stage: "saved", receipt: result.value });
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
  private publish(state: DocumentRetirementState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
