import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  AnnouncementCommand,
  AnnouncementCommandKind,
} from "../../domain/entities/announcement-command";
import {
  announcementCommandWasRejected,
  normalizeAnnouncementCommand,
  validateAnnouncementCommand,
} from "../../domain/policies/announcement-command-policy";
import type { CommunicationsUseCases } from "../contracts/communications-use-cases";
import {
  type AnnouncementCommandState,
  initialAnnouncementCommandState,
} from "../models/announcement-command-state";

type Actions = Pick<
  CommunicationsUseCases,
  "loadReview" | "previewAudience" | "publish" | "archive" | "returnToDraft"
>;
type Submission = Readonly<{ operation: OperationId; input: AnnouncementCommand }>;
export type AnnouncementCommandFields = Readonly<{ reason: string; scheduledFor: string | null }>;

export class AnnouncementCommandController {
  #state = initialAnnouncementCommandState;
  #active = false;
  #pending: AbortController | null = null;
  #prepared: AnnouncementCommand | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Actions,
    private readonly access: CompanyAccess,
    private readonly id: string,
    readonly kind: AnnouncementCommandKind,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): AnnouncementCommandState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    if (!this.#active) {
      this.#active = true;
      void this.refresh();
    }
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.#prepared = null;
    this.#submission = null;
    this.publish(initialAnnouncementCommandState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#prepared = null;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialAnnouncementCommandState);
    try {
      const loaded = await this.actions.loadReview.execute(this.access, this.id, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (!loaded.ok) {
        this.publish({
          ...initialAnnouncementCommandState,
          stage: "unavailable",
          failure: loaded.failure,
        });
        return;
      }
      const review = loaded.value;
      if (!review.availableActions.includes(this.kind)) {
        this.publish({
          ...initialAnnouncementCommandState,
          stage: "unavailable",
          review,
          failure: { code: "announcement_action_unavailable", fields: {}, parameters: {} },
        });
        return;
      }
      const preview =
        this.kind === "PUBLISH"
          ? await this.actions.previewAudience.execute(
              this.access,
              review.announcement,
              pending.signal,
            )
          : null;
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        preview && !preview.ok
          ? {
              ...initialAnnouncementCommandState,
              stage: "unavailable",
              review,
              failure: preview.failure,
            }
          : {
              ...initialAnnouncementCommandState,
              stage: "editing",
              review,
              preview: preview?.ok ? preview.value : null,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialAnnouncementCommandState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  prepare = (fields: AnnouncementCommandFields): void => {
    const review = this.#state.review;
    if (!this.#active || this.#state.stage !== "editing" || !review) return;
    const common = {
      id: review.announcement.id,
      expectedVersion: review.announcement.version,
      reason: fields.reason,
    };
    const input = normalizeAnnouncementCommand(
      this.kind === "PUBLISH"
        ? { ...common, action: this.kind, scheduledFor: fields.scheduledFor }
        : { ...common, action: this.kind },
    );
    const invalid = validateAnnouncementCommand(input);
    if (invalid) {
      this.publish({ ...this.#state, failure: invalid });
      return;
    }
    this.#prepared = input;
    this.publish({ ...this.#state, stage: "confirming", failure: null });
  };
  dismiss = (): void => {
    if (this.#state.stage !== "confirming") return;
    this.#prepared = null;
    this.publish({ ...this.#state, stage: "editing" });
  };
  confirm = async (): Promise<void> => {
    if (!this.#active || this.#state.stage !== "confirming" || !this.#prepared) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: this.#prepared,
    });
    this.#prepared = null;
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
        const input = submission.input;
        switch (input.action) {
          case "PUBLISH":
            result = await this.actions.publish.execute(
              this.access,
              submission.operation,
              input,
              pending.signal,
            );
            break;
          case "ARCHIVE":
            result = await this.actions.archive.execute(
              this.access,
              submission.operation,
              input,
              pending.signal,
            );
            break;
          case "RETURN_TO_DRAFT":
            result = await this.actions.returnToDraft.execute(
              this.access,
              submission.operation,
              input,
              pending.signal,
            );
            break;
        }
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#submission = null;
        this.publish({ ...initialAnnouncementCommandState, stage: "saved", receipt: result.value });
      } else if (!wasUnconfirmed && announcementCommandWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          operationId: null,
          failure: result.failure,
          stage: [
            "stale_version",
            "announcement_not_found",
            "announcement_not_draft",
            "announcement_archived",
            "announcement_revision_limit",
            "announcement_attempt_limit",
            "announcement_publication_active",
            "announcement_publication_not_stopped",
          ].includes(result.failure.code)
            ? "conflict"
            : "editing",
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: AnnouncementCommandState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
