import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentRevision } from "../../domain/entities/document-revision";
import type {
  DocumentUploadFields,
  DocumentUploadFile,
  DocumentUploadPlan,
  DocumentUploadTarget,
} from "../../domain/entities/document-upload";
import {
  documentActionAccessLost,
  documentActionReviewChanged,
  documentActionWasRejected,
} from "../../domain/policies/document-action-policy";
import {
  documentUploadPartCount,
  prepareDocumentUpload,
} from "../../domain/policies/document-upload-policy";
import type { DocumentsUseCases } from "../contracts/documents-use-cases";
import {
  type DocumentUploadState,
  initialDocumentUploadState,
} from "../models/document-upload-state";

/** One route owns its selected file, immutable command plan and cancellable operation. */
export class DocumentUploadController {
  #state = initialDocumentUploadState;
  #active = false;
  #visible = true;
  #pending: AbortController | null = null;
  #file: DocumentUploadFile | null = null;
  #plan: DocumentUploadPlan | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      DocumentsUseCases,
      "reviewUpload" | "selectUploadFile" | "releaseUploadFile" | "uploadFile"
    >,
    private readonly access: CompanyAccess,
    readonly target: DocumentUploadTarget,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): DocumentUploadState => this.#state;
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
    if (this.#file) this.actions.releaseUploadFile.execute(this.#file.reference.id);
    this.#file = null;
    this.#plan = null;
    this.publish(initialDocumentUploadState);
  };
  setVisible = (visible: boolean): void => {
    this.#visible = visible;
    if (visible) return;
    this.#pending?.abort();
    this.#pending = null;
    if (this.#plan) this.publish({ ...this.#state, stage: "paused" });
    else if (this.#state.stage === "selecting") this.publish({ ...this.#state, stage: "editing" });
    else if (this.#state.stage === "loading")
      this.publish({ ...this.#state, stage: "unavailable" });
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || !this.#visible || this.#plan) return;
    this.#pending?.abort();
    if (this.#file) this.actions.releaseUploadFile.execute(this.#file.reference.id);
    this.#file = null;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialDocumentUploadState);
    try {
      const result = await this.actions.reviewUpload.execute(
        this.access,
        this.target,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialDocumentUploadState, stage: "editing", review: result.value }
          : { ...initialDocumentUploadState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialDocumentUploadState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  selectFile = async (): Promise<void> => {
    const review = this.#state.review;
    if (
      !this.#active ||
      !this.#visible ||
      this.#pending ||
      this.#state.stage !== "editing" ||
      !review
    )
      return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...this.#state, stage: "selecting", failure: null });
    try {
      const result = await this.actions.selectUploadFile.execute(
        this.access,
        review,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) {
        if (result.ok && result.value)
          this.actions.releaseUploadFile.execute(result.value.reference.id);
        return;
      }
      if (result.ok && result.value) {
        if (this.#file) this.actions.releaseUploadFile.execute(this.#file.reference.id);
        this.#file = result.value;
      }
      const lost = !result.ok && documentActionAccessLost(result.failure);
      this.publish({
        ...this.#state,
        stage: lost ? "unavailable" : "editing",
        review: lost ? null : review,
        file:
          lost || !this.#file
            ? null
            : Object.freeze({
                name: this.#file.reference.name,
                byteLength: this.#file.reference.byteLength,
              }),
        failure: result.ok ? null : result.failure,
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
  confirm = async (fields: DocumentUploadFields): Promise<void> => {
    const review = this.#state.review,
      file = this.#file;
    if (
      !this.#active ||
      !this.#visible ||
      this.#pending ||
      this.#state.stage !== "editing" ||
      !review
    )
      return;
    if (!file) {
      this.publish({
        ...this.#state,
        failure: { code: "document_file_required", fields: {}, parameters: {} },
      });
      return;
    }
    try {
      const plan = prepareDocumentUpload(this.access, review, file, fields, {
        documentId: review.kind === "new" ? this.nextIdentifier() : review.document.id,
        revisionId: review.kind === "resume" ? review.revision.id : this.nextIdentifier(),
        startOperation: review.kind === "resume" ? null : this.nextIdentifier(),
        chunkOperations: Array.from(
          { length: documentUploadPartCount(file.reference.byteLength) },
          () => this.nextIdentifier(),
        ),
      });
      if (!plan.ok) {
        this.publish({ ...this.#state, failure: plan.failure });
        return;
      }
      this.#plan = plan.value;
    } catch {
      this.publish({
        ...this.#state,
        failure: { code: "unexpected_error", fields: {}, parameters: {} },
      });
      return;
    }
    await this.submit(false);
  };
  pause = (): void => {
    if (!this.#active || this.#state.stage !== "uploading") return;
    this.#pending?.abort();
    this.#pending = null;
    this.publish({ ...this.#state, stage: "paused" });
  };
  retry = async (): Promise<void> => {
    if (this.#active && this.#visible && !this.#pending && this.#state.stage === "paused")
      await this.submit(true);
  };
  private async submit(wasUnconfirmed: boolean): Promise<void> {
    const plan = this.#plan;
    if (!plan) return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({
      ...this.#state,
      stage: "uploading",
      failure: null,
    });
    try {
      let result: Result<DocumentRevision>;
      try {
        result = await this.actions.uploadFile.execute(
          this.access,
          plan,
          (uploadedBytes) => {
            if (!pending.signal.aborted && this.#pending === pending)
              this.publish({
                ...this.#state,
                uploadedBytes: Math.max(this.#state.uploadedBytes ?? 0, uploadedBytes),
              });
          },
          pending.signal,
        );
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#plan = null;
        if (this.#file) this.actions.releaseUploadFile.execute(this.#file.reference.id);
        this.#file = null;
        this.publish({
          ...this.#state,
          stage: "complete",
          file: null,
          failure: null,
          completion: result.value,
        });
        return;
      }
      const lost = documentActionAccessLost(result.failure);
      const rejected =
        !wasUnconfirmed &&
        plan.start !== null &&
        this.#state.uploadedBytes === null &&
        (documentActionWasRejected(result.failure) ||
          [
            "invalid_document_upload",
            "invalid_document_metadata",
            "document_metadata_mismatch",
            "document_identity_mismatch",
            "document_revision_limit",
            "document_upload_active",
            "document_upload_capacity",
            "document_storage_quota",
          ].includes(result.failure.code));
      if (rejected) this.#plan = null;
      this.publish({
        ...this.#state,
        review: lost ? null : this.#state.review,
        file: lost ? null : this.#state.file,
        failure: result.failure,
        stage: !rejected
          ? "paused"
          : lost
            ? "unavailable"
            : documentActionReviewChanged(result.failure) ||
                result.failure.code === "document_upload_active" ||
                result.failure.code === "document_metadata_mismatch"
              ? "conflict"
              : "editing",
      });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: DocumentUploadState): void {
    this.#state = Object.freeze(state);
    for (const listener of this.#listeners) listener();
  }
}
