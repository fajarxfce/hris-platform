import { failed, type Result } from "../../../../core/domain/result";
import type { AccountDeviceSessionDirectory } from "../../domain/entities/account-device-session-directory";
import type { PlatformAccess } from "../../domain/entities/platform-access";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import {
  type AccountDeviceSessionsState,
  initialAccountDeviceSessionsState,
} from "../models/account-device-sessions-state";

const accessFailures = new Set([
  "authentication_required",
  "session_revoked",
  "unauthenticated",
  "platform_administrator_required",
  "account_not_found",
  "access_denied",
  "mfa_required",
  "mfa_setup_required",
  "recent_authentication_required",
]);

/** One initiating account and selected account own one read or revocation. */
export class AccountDeviceSessionsController {
  #state = initialAccountDeviceSessionsState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      IdentityAdministrationUseCases,
      "loadAccountDeviceSessions" | "revokeAccountDeviceSession"
    >,
    private readonly access: PlatformAccess,
    private readonly accountId: string,
  ) {}
  getSnapshot = (): AccountDeviceSessionsState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    if (this.#active) return;
    this.#active = true;
    if (this.#state.stage !== "unavailable") void this.refresh();
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    const failure = this.#state.failure;
    // Re-admitting unchanged authority cannot silently repeat a rejected read.
    this.publish(
      failure && accessFailures.has(failure.code)
        ? { ...initialAccountDeviceSessionsState, stage: "unavailable", failure }
        : initialAccountDeviceSessionsState,
    );
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || ["confirming", "revoking"].includes(this.#state.stage)) return;
    const { selected, account } = this.#state;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...initialAccountDeviceSessionsState, account, selected });
    let result: Result<AccountDeviceSessionDirectory>;
    try {
      result = await this.actions.loadAccountDeviceSessions.execute(
        this.access,
        this.accountId,
        pending.signal,
      );
    } catch {
      result = failed("unexpected_error");
    }
    if (pending.signal.aborted || this.#pending !== pending) return;
    this.#pending = null;
    if (result.ok) {
      this.publish({
        ...initialAccountDeviceSessionsState,
        stage: "ready",
        account: result.value.account,
        sessions: result.value.sessions,
        ended: selected !== null && !result.value.sessions.some((item) => item.id === selected.id),
      });
    } else {
      const denied = accessFailures.has(result.failure.code);
      this.publish({
        ...initialAccountDeviceSessionsState,
        stage: selected && !denied ? "unconfirmed" : "unavailable",
        account: denied ? null : account,
        selected: denied ? null : selected,
        failure: result.failure,
      });
    }
  };
  review = (id: string): void => {
    if (!this.#active || this.#state.stage !== "ready") return;
    const selected = this.#state.sessions?.find((item) => item.id === id);
    if (selected) this.publish({ ...this.#state, stage: "confirming", selected, ended: false });
  };
  dismissReview = (): void => {
    if (this.#state.stage === "confirming")
      this.publish({ ...this.#state, stage: "ready", selected: null });
  };
  revoke = async (): Promise<void> => {
    const selected = this.#state.selected;
    if (!this.#active || !selected || !["confirming", "unconfirmed"].includes(this.#state.stage))
      return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...this.#state, stage: "revoking", failure: null });
    let result: Result<null>;
    try {
      result = await this.actions.revokeAccountDeviceSession.execute(
        this.access,
        this.accountId,
        selected.id,
        pending.signal,
      );
    } catch {
      result = failed("unexpected_error");
    }
    if (pending.signal.aborted || this.#pending !== pending) return;
    this.#pending = null;
    if (result.ok) {
      this.publish({
        ...initialAccountDeviceSessionsState,
        stage: "ready",
        account: this.#state.account,
        sessions:
          this.#state.sessions === null
            ? null
            : Object.freeze(this.#state.sessions.filter((item) => item.id !== selected.id)),
        ended: true,
      });
    } else if (accessFailures.has(result.failure.code)) {
      this.publish({
        ...initialAccountDeviceSessionsState,
        stage: "unavailable",
        failure: result.failure,
      });
    } else {
      this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    }
  };
  private publish(state: AccountDeviceSessionsState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
