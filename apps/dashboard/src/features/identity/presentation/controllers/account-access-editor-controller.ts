import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { AccountAccessChange } from "../../domain/entities/account-access-change";
import type { PlatformAccess } from "../../domain/entities/platform-access";
import { accountAccessSaveWasRejected } from "../../domain/policies/account-access-policy";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import {
  type AccountAccessEditorState,
  initialAccountAccessEditorState,
} from "../models/account-access-editor-state";
export type AccountAccessFields = Omit<AccountAccessChange, "accountId" | "expectedVersion">;
type Submission = Readonly<{ operation: OperationId; input: AccountAccessChange }>;
export class AccountAccessEditorController {
  #state = initialAccountAccessEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      IdentityAdministrationUseCases,
      "loadManagedAccount" | "saveAccountAccess"
    >,
    private readonly access: PlatformAccess,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): AccountAccessEditorState => this.#state;
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
    this.publish(initialAccountAccessEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialAccountAccessEditorState);
    try {
      const result = await this.actions.loadManagedAccount.execute(
        this.access,
        this.id,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialAccountAccessEditorState, stage: "editing", review: result.value }
          : { ...initialAccountAccessEditorState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialAccountAccessEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: AccountAccessFields): Promise<void> => {
    const account = this.#state.review?.account;
    if (!this.#active || this.#state.stage !== "editing" || !account) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        accountId: account.id,
        expectedVersion: account.version,
        active: fields.active,
        reason: fields.reason,
        platformPermissions: Object.freeze([...fields.platformPermissions]),
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
        result = await this.actions.saveAccountAccess.execute(
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
        this.publish({ ...initialAccountAccessEditorState, stage: "saved", receipt: result.value });
      } else if (!wasUnconfirmed && accountAccessSaveWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          failure: result.failure,
          operationId: null,
          stage: ["stale_version", "account_not_found", "invitation_acceptance_required"].includes(
            result.failure.code,
          )
            ? "conflict"
            : "editing",
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: AccountAccessEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
