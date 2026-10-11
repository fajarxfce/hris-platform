import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { AudienceGroupChange } from "../../domain/entities/audience-group-change";
import { canManageAnnouncements } from "../../domain/policies/announcement-read-policy";
import { audienceGroupSaveWasRejected } from "../../domain/policies/audience-group-policy";
import type { CommunicationsUseCases } from "../contracts/communications-use-cases";
import {
  type AudienceGroupEditorState,
  initialAudienceGroupEditorState,
} from "../models/audience-group-editor-state";

export type AudienceGroupFields = Omit<AudienceGroupChange, "id" | "expectedVersion">;
type Submission = Readonly<{ operation: OperationId; input: AudienceGroupChange }>;

export class AudienceGroupEditorController {
  #state = initialAudienceGroupEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<CommunicationsUseCases, "loadGroup" | "saveGroup">,
    private readonly access: CompanyAccess,
    readonly creating: boolean,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): AudienceGroupEditorState => this.#state;
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
    this.publish(initialAudienceGroupEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialAudienceGroupEditorState);
    if (!canManageAnnouncements(this.access.permissions)) {
      this.#pending = null;
      this.publish({
        ...initialAudienceGroupEditorState,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
      return;
    }
    if (this.creating) {
      this.#pending = null;
      this.publish({ ...initialAudienceGroupEditorState, stage: "editing" });
      return;
    }
    try {
      const result = await this.actions.loadGroup.execute(
        this.access,
        this.id,
        null,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialAudienceGroupEditorState, stage: "editing", group: result.value }
          : { ...initialAudienceGroupEditorState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialAudienceGroupEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: AudienceGroupFields): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing") return;
    const original = this.#state.group;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        ...fields,
        id: original?.id ?? this.id,
        expectedVersion: original?.version ?? null,
        employmentIds: Object.freeze([...fields.employmentIds]),
      }),
    });
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
        result = await this.actions.saveGroup.execute(
          this.access,
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
        this.publish({ ...initialAudienceGroupEditorState, stage: "saved", receipt: result.value });
      } else if (!wasUnconfirmed && audienceGroupSaveWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          operationId: null,
          failure: result.failure,
          stage: [
            "stale_version",
            "audience_group_not_found",
            "audience_group_revision_limit",
          ].includes(result.failure.code)
            ? "conflict"
            : "editing",
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: AudienceGroupEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
