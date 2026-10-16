import { failed, type Result } from "../../../../core/domain/result";
import type { AccountCredentialUseCases } from "../contracts/account-credential-use-cases";
import {
  initialPasswordRecoveryState,
  type PasswordRecoveryState,
} from "../models/password-recovery-state";
export class PasswordRecoveryController {
  #state = initialPasswordRecoveryState;
  #active = false;
  #pending: AbortController | null = null;
  #listeners = new Set<() => void>();
  constructor(private readonly action: AccountCredentialUseCases["requestPasswordRecovery"]) {}
  getSnapshot = (): PasswordRecoveryState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    this.#active = true;
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.publish(initialPasswordRecoveryState);
  };
  request = async (email: string): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing" || this.#pending) return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ stage: "sending", failure: null });
    try {
      let result: Result<void>;
      try {
        result = await this.action.execute(email, pending.signal);
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { stage: "sent", failure: null }
          : { stage: "editing", failure: result.failure },
      );
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  private publish(state: PasswordRecoveryState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
