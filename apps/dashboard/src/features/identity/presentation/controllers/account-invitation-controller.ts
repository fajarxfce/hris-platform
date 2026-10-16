import type { AccountId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { AccountInvitation } from "../../domain/entities/account-invitation";
import type { PlatformAccess } from "../../domain/entities/platform-access";
import { canManagePlatformAccounts } from "../../domain/policies/account-access-policy";
import { accountInvitationWasRejected } from "../../domain/policies/account-invitation-policy";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import {
  type AccountInvitationState,
  initialAccountInvitationState,
} from "../models/account-invitation-state";

export type AccountInvitationFields = Pick<AccountInvitation, "email" | "displayName" | "reason">;
type Submission = Readonly<{ operation: OperationId; input: AccountInvitation }>;
export class AccountInvitationController {
  #state = initialAccountInvitationState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      IdentityAdministrationUseCases,
      "loadManagedAccount" | "inviteAccount"
    >,
    private readonly access: PlatformAccess,
    private readonly id: string | null,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): AccountInvitationState => this.#state;
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
    this.publish(initialAccountInvitationState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialAccountInvitationState);
    try {
      if (!canManagePlatformAccounts(this.access)) {
        this.publish({
          ...initialAccountInvitationState,
          stage: "unavailable",
          failure: { code: "platform_administrator_required", fields: {}, parameters: {} },
        });
        return;
      }
      if (this.id === null) {
        this.publish({ ...initialAccountInvitationState, stage: "editing" });
        return;
      }
      const result = await this.actions.loadManagedAccount.execute(
        this.access,
        this.id,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (!result.ok)
        this.publish({
          ...initialAccountInvitationState,
          stage: "unavailable",
          failure: result.failure,
        });
      else if (result.value.account.active || !result.value.account.invitationPending)
        this.publish({
          ...initialAccountInvitationState,
          stage: "unavailable",
          failure: { code: "account_cannot_be_invited", fields: {}, parameters: {} },
        });
      else
        this.publish({ ...initialAccountInvitationState, stage: "editing", review: result.value });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialAccountInvitationState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: AccountInvitationFields): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing") return;
    const account = this.#state.review?.account;
    if (this.id !== null && !account) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        accountId: account?.id ?? (this.nextIdentifier() as AccountId),
        expectedVersion: account?.version ?? null,
        email: account?.email ?? fields.email,
        displayName: fields.displayName,
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
        result = await this.actions.inviteAccount.execute(
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
        this.publish({ ...initialAccountInvitationState, stage: "saved", receipt: result.value });
      } else if (!wasUnconfirmed && accountInvitationWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          failure: result.failure,
          operationId: null,
          stage: ["stale_version", "account_not_found", "account_cannot_be_invited"].includes(
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
  private publish(state: AccountInvitationState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
