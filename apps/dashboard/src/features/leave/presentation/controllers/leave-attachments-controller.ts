import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LeaveAttachmentSource } from "../../domain/entities/leave-attachment";
import type { LeaveUseCases } from "../contracts/leave-use-cases";
import {
  initialLeaveAttachmentsState,
  type LeaveAttachmentsState,
} from "../models/leave-attachments-state";

export class LeaveAttachmentsController {
  #active = false;
  #pending: AbortController | null = null;
  #state = initialLeaveAttachmentsState;
  #listeners = new Set<() => void>();
  constructor(
    private readonly download: LeaveUseCases["downloadAttachment"],
    private readonly access: CompanyAccess,
    private readonly request: LeaveAttachmentSource,
  ) {}
  getSnapshot = (): LeaveAttachmentsState => this.#state;
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
    this.publish(initialLeaveAttachmentsState);
  };
  open = async (revisionId: string): Promise<void> => {
    if (!this.#active || this.#pending || this.#state.stage === "unavailable") return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "downloading", revisionId, failure: null });
    try {
      const result = await this.download.execute(
        this.access,
        this.request,
        revisionId,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "started", revisionId, failure: null }
          : {
              stage: [
                "access_denied",
                "company_access_denied",
                "leave_attachment_not_found",
              ].includes(result.failure.code)
                ? "unavailable"
                : "failed",
              revisionId,
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          stage: "failed",
          revisionId,
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: LeaveAttachmentsState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
