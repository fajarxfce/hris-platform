import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ClientPolicyChange } from "../../domain/entities/client-policy-change";
import { clientPolicyChangeWasRejected } from "../../domain/policies/client-policy-change-policy";
import type { AdministrationUseCases } from "../contracts/administration-use-cases";
import {
  type ClientPolicyEditorState,
  initialClientPolicyEditorState,
} from "../models/client-policy-editor-state";

type Submission = Readonly<{ operation: OperationId; input: ClientPolicyChange }>;
export class ClientPolicyEditorController {
  #state = initialClientPolicyEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<AdministrationUseCases, "loadClientPolicy" | "saveClientPolicy">,
    private readonly access: CompanyAccess,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): ClientPolicyEditorState => this.#state;
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
    this.publish(initialClientPolicyEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialClientPolicyEditorState);
    try {
      const result = await this.actions.loadClientPolicy.execute(this.access, null, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? {
              ...initialClientPolicyEditorState,
              stage: "editing",
              settings: result.value.settings,
              failure:
                result.value.settings.latest?.version === 9999
                  ? { code: "client_policy_revision_limit", fields: {}, parameters: {} }
                  : null,
            }
          : { ...initialClientPolicyEditorState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialClientPolicyEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: Omit<ClientPolicyChange, "expectedVersion">): Promise<void> => {
    const settings = this.#state.settings;
    if (
      !this.#active ||
      this.#state.stage !== "editing" ||
      !settings ||
      settings.latest?.version === 9999
    )
      return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        expectedVersion: settings.latest?.version ?? null,
        activateAt: fields.activateAt,
        disabledModules: Object.freeze([...fields.disabledModules]),
        minimumBuilds: Object.freeze({
          android: fields.minimumBuilds.android,
          ios: fields.minimumBuilds.ios,
          web: fields.minimumBuilds.web,
        }),
        maintenance:
          fields.maintenance === null
            ? null
            : Object.freeze({
                startsAt: fields.maintenance.startsAt,
                endsAt: fields.maintenance.endsAt,
              }),
        reason: fields.reason,
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
        result = await this.actions.saveClientPolicy.execute(
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
        this.publish({
          ...this.#state,
          stage: "saved",
          receipt: result.value,
          failure: null,
          operationId: null,
        });
      } else if (!wasUnconfirmed && clientPolicyChangeWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          failure: result.failure,
          operationId: null,
          stage: ["stale_version", "client_policy_revision_limit"].includes(result.failure.code)
            ? "conflict"
            : "editing",
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: ClientPolicyEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
