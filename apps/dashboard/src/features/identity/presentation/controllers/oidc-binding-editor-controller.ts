import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { OidcBindingId } from "../../domain/entities/oidc-binding";
import type { OidcBindingChange } from "../../domain/entities/oidc-binding-change";
import type { PlatformAccess } from "../../domain/entities/platform-access";
import { oidcBindingSaveWasRejected } from "../../domain/policies/oidc-binding-policy";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import {
  initialOidcBindingEditorState,
  type OidcBindingEditorState,
} from "../models/oidc-binding-editor-state";

export type OidcBindingFields = Pick<OidcBindingChange, "issuer" | "subject" | "active" | "reason">;
type Submission = Readonly<{ operation: OperationId; input: OidcBindingChange }>;
export class OidcBindingEditorController {
  #state = initialOidcBindingEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      IdentityAdministrationUseCases,
      "prepareOidcBinding" | "saveOidcBinding"
    >,
    private readonly access: PlatformAccess,
    private readonly accountId: string,
    private readonly bindingId: string | null,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): OidcBindingEditorState => this.#state;
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
    this.publish(initialOidcBindingEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialOidcBindingEditorState);
    try {
      const result = await this.actions.prepareOidcBinding.execute(
        this.access,
        this.accountId,
        this.bindingId,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialOidcBindingEditorState, stage: "editing", review: result.value }
          : { ...initialOidcBindingEditorState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialOidcBindingEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: OidcBindingFields): Promise<void> => {
    const review = this.#state.review;
    if (!this.#active || this.#state.stage !== "editing" || !review) return;
    if (review.binding?.active === fields.active) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        accountId: review.account.id,
        id: review.binding?.id ?? (this.nextIdentifier() as OidcBindingId),
        issuer: review.binding?.issuer ?? fields.issuer,
        subject: review.binding?.subject ?? fields.subject,
        active: fields.active,
        expectedVersion: review.binding?.version ?? null,
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
        result = await this.actions.saveOidcBinding.execute(
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
        this.publish({ ...initialOidcBindingEditorState, stage: "saved", receipt: result.value });
      } else if (!wasUnconfirmed && oidcBindingSaveWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          failure: result.failure,
          operationId: null,
          stage: [
            "stale_version",
            "account_not_found",
            "account_disabled",
            "oidc_identity_not_found",
            "oidc_binding_immutable",
          ].includes(result.failure.code)
            ? "conflict"
            : "editing",
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: OidcBindingEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
