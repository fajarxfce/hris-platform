import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentsUseCases } from "../contracts/documents-use-cases";
import {
  type DocumentDownloadState,
  initialDocumentDownloadState,
} from "../models/document-download-state";

/** One displayed, freshly read revision owns one cancellable download; bytes stay in domain/data. */
export class DocumentDownloadController {
  #active = false;
  #pending: AbortController | null = null;
  #state = initialDocumentDownloadState;
  #listeners = new Set<() => void>();
  constructor(
    private readonly download: DocumentsUseCases["download"],
    private readonly access: CompanyAccess,
    private readonly documentId: string,
    private readonly revisionId: string,
  ) {}
  getSnapshot = (): DocumentDownloadState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    this.#active = true;
  };
  deactivate = (): void => {
    this.#active = false;
    this.cancel();
  };
  cancel = (): void => {
    this.#pending?.abort();
    this.#pending = null;
    // Cancellation cannot restore a resource whose access was rejected.
    if (this.#state.stage === "downloading") this.publish(initialDocumentDownloadState);
  };
  start = async (): Promise<void> => {
    if (!this.#active || this.#pending || this.#state.stage === "unavailable") return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "downloading", failure: null });
    try {
      const result = await this.download.execute(
        this.access,
        this.documentId,
        this.revisionId,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "started", failure: null }
          : {
              stage: [
                "access_denied",
                "company_access_denied",
                "authentication_required",
                "unauthenticated",
                "session_revoked",
                "mfa_required",
                "mfa_setup_required",
                "recent_authentication_required",
                "document_access_denied",
                "document_not_found",
                "document_revision_not_found",
                "resource_not_found",
                "document_not_ready",
                "company_module_unavailable",
              ].includes(result.failure.code)
                ? "unavailable"
                : "failed",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "failed",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: DocumentDownloadState): void {
    this.#state = Object.freeze(state);
    for (const listener of this.#listeners) listener();
  }
}
