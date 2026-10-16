import { failed, type Result } from "../../../../core/domain/result";
import type { AccountCredentialKind } from "../../domain/entities/account-credential";
import {
  credentialConfirmationWasRejected,
  validCredentialToken,
} from "../../domain/policies/account-credential-policy";
import type { AccountCredentialUseCases } from "../contracts/account-credential-use-cases";
import {
  type CredentialConfirmationState,
  initialCredentialConfirmationState,
} from "../models/credential-confirmation-state";

/** Owns one in-memory link. Terminal outcomes discard it; confirmation is never replayed. */
export class CredentialConfirmationController {
  #state = initialCredentialConfirmationState;
  #token: string | null = null;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      AccountCredentialUseCases,
      "confirmAccountCredential" | "signOut"
    >,
    private readonly kind: AccountCredentialKind,
  ) {}
  getSnapshot = (): CredentialConfirmationState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  capture = (token: string | null): void => {
    this.#pending?.abort();
    this.#pending = null;
    this.#token = token && validCredentialToken(token) ? token : null;
    this.publish(
      this.#token
        ? { stage: "editing", leaving: false, failure: null }
        : initialCredentialConfirmationState,
    );
  };
  activate = (): void => {
    this.#active = true;
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    if (this.#state.stage === "submitting") {
      this.#token = null;
      this.publish({ stage: "unconfirmed", leaving: false, failure: null });
    }
  };
  confirm = async (password: string): Promise<void> => {
    const token = this.#token;
    if (!this.#active || this.#state.stage !== "editing" || !token || this.#pending) return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "submitting", leaving: false, failure: null });
    try {
      let result: Result<void>;
      try {
        result = await this.actions.confirmAccountCredential.execute(
          this.kind,
          { token, password },
          pending.signal,
        );
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#token = null;
        this.publish({ stage: "saved", leaving: false, failure: null });
      } else if (result.failure.code === "credential_link_unavailable") {
        this.#token = null;
        this.publish({ stage: "unavailable", leaving: false, failure: result.failure });
      } else if (credentialConfirmationWasRejected(result.failure))
        this.publish({ stage: "editing", leaving: false, failure: result.failure });
      else {
        this.#token = null;
        this.publish({ stage: "unconfirmed", leaving: false, failure: result.failure });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  continueToSignIn = async (): Promise<void> => {
    if (
      !this.#active ||
      this.#pending ||
      !["saved", "unconfirmed", "unavailable"].includes(this.#state.stage)
    )
      return;
    const pending = new AbortController();
    this.#pending = pending;
    this.#token = null;
    this.publish({ ...this.#state, leaving: true, failure: null });
    try {
      let result: Result<void>;
      try {
        result = await this.actions.signOut.execute(pending.signal);
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "signedOut", leaving: false, failure: null }
          : { ...this.#state, leaving: false, failure: result.failure },
      );
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: CredentialConfirmationState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
