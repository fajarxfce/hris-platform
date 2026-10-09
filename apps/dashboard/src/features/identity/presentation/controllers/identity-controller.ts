import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { MfaVerification } from "../../domain/entities/mfa";
import type { SignInCredentials } from "../../domain/entities/sign-in";
import type { IdentityUseCases } from "../contracts/identity-use-cases";
import { type IdentityState, initialIdentityState } from "../models/identity-state";

/** Owns screen requests and one-use secrets for one mounted application. */
export class IdentityController {
  #state: IdentityState = initialIdentityState;
  #listeners = new Set<() => void>();
  #active = false;
  #pending: AbortController | null = null;
  #providers: AbortController | null = null;
  #enrollmentId: OperationId | null = null;

  constructor(
    private readonly useCases: IdentityUseCases,
    private readonly nextOperationId: () => OperationId,
    private readonly clearPrivateCache: () => void,
  ) {}

  getSnapshot = (): IdentityState => this.#state;

  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };

  activate = (): void => {
    if (this.#active) return;
    this.#active = true;
    void this.refreshSession();
    void this.loadProviders();
  };

  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#providers?.abort();
    this.#pending = null;
    this.#providers = null;
    this.#enrollmentId = null;
    this.clearPrivateCache();
    this.update(initialIdentityState);
  };

  refreshSession = async (): Promise<void> => {
    if (this.#state.busy || this.#state.stage === "recoveryCodes") return;
    await this.runOwned(async (signal) => {
      this.update({ stage: "loading", access: null, failure: null });
      await this.resolveSession(signal);
    });
  };

  signIn = async (credentials: SignInCredentials): Promise<void> => {
    if (this.#state.busy || this.#state.stage !== "signedOut") return;
    await this.runOwned(async (signal) => {
      const result = await this.useCases.signIn.execute(credentials, signal);
      if (signal.aborted) return;
      if (!result.ok) return this.update({ failure: result.failure });
      this.clearPrivateCache();
      this.update({ stage: "loading" });
      await this.resolveSession(signal);
    });
  };

  signOut = async (): Promise<void> => {
    this.#enrollmentId = null;
    this.clearPrivateCache();
    this.update({ ...initialIdentityState, stage: "signedOut", providers: this.#state.providers });
    await this.runOwned(async (signal) => {
      const result = await this.useCases.signOut.execute(signal);
      if (signal.aborted) return;
      if (!result.ok) this.update({ failure: result.failure });
    });
  };

  selectCompany = async (companyId: CompanyId): Promise<void> => {
    const session = this.#state.session;
    if (
      !session ||
      (this.#state.stage !== "ready" &&
        this.#state.stage !== "unavailable" &&
        this.#state.stage !== "loading")
    )
      return;
    this.clearPrivateCache();
    await this.runOwned(async (signal) => {
      this.update({ stage: "loading", companyId, access: null });
      const access = await this.useCases.loadCompanyAccess.execute(session, companyId, signal);
      if (signal.aborted) return;
      if (!access.ok) return this.update({ stage: "unavailable", failure: access.failure });
      this.update({ stage: "ready", access: access.value });
    });
  };

  beginEnrollment = async (): Promise<void> => {
    if (
      this.#state.busy ||
      this.#state.stage !== "challenge" ||
      this.#state.session?.account.mfaConfigured
    )
      return;
    this.#enrollmentId ??= this.nextOperationId();
    const id = this.#enrollmentId;
    await this.runOwned(async (signal) => {
      const result = await this.useCases.beginEnrollment.execute(id, signal);
      if (signal.aborted) return;
      if (!result.ok) {
        if (["mfa_enrollment_expired", "mfa_enrollment_changed"].includes(result.failure.code)) {
          this.#enrollmentId = null;
          this.update({ enrollment: null });
        }
        return this.update({ failure: result.failure });
      }
      this.update({ enrollment: result.value });
    });
  };

  confirmEnrollment = async (code: string): Promise<void> => {
    const enrollment = this.#state.enrollment;
    if (this.#state.busy || this.#state.stage !== "challenge" || !enrollment) return;
    await this.runOwned(async (signal) => {
      const result = await this.useCases.confirmEnrollment.execute(
        enrollment.operationId,
        code,
        signal,
      );
      if (signal.aborted) return;
      if (!result.ok) {
        if (["mfa_enrollment_expired", "mfa_enrollment_changed"].includes(result.failure.code)) {
          this.#enrollmentId = null;
          this.update({ enrollment: null });
        }
        return this.update({ failure: result.failure });
      }
      await this.acceptVerification(result.value, signal);
    });
  };

  verifyMfa = async (code: string, recovery: boolean): Promise<void> => {
    if (this.#state.busy || this.#state.stage !== "challenge") return;
    await this.runOwned(async (signal) => {
      const result = await this.useCases.verifyMfa.execute(code, recovery, signal);
      if (signal.aborted) return;
      if (!result.ok) return this.update({ failure: result.failure });
      await this.acceptVerification(result.value, signal);
    });
  };

  acknowledgeRecoveryCodes = async (): Promise<void> => {
    if (this.#state.busy || this.#state.stage !== "recoveryCodes") return;
    this.update({ recoveryCodes: [], stage: "loading" });
    await this.refreshSession();
  };

  private update(patch: Partial<IdentityState>): void {
    this.#state = { ...this.#state, ...patch };
    for (const listener of this.#listeners) listener();
  }

  private async runOwned(operation: (signal: AbortSignal) => Promise<void>): Promise<void> {
    if (!this.#active) return;
    this.#pending?.abort();
    const controller = new AbortController();
    this.#pending = controller;
    this.update({ busy: true, failure: null });
    try {
      await operation(controller.signal);
    } catch {
      if (!controller.signal.aborted) {
        const failure: Failure = { code: "unexpected_error", fields: {}, parameters: {} };
        this.update({
          failure,
          ...(this.#state.stage === "loading" ? { stage: "unavailable" } : {}),
        });
      }
    } finally {
      if (this.#pending === controller) {
        this.#pending = null;
        this.update({ busy: false });
      }
    }
  }

  private async resolveSession(signal: AbortSignal): Promise<void> {
    const result = await this.useCases.loadSession.execute(signal);
    if (signal.aborted) return;
    if (!result.ok) {
      this.clearPrivateCache();
      const signedOut = ["authentication_required", "session_revoked", "unauthenticated"].includes(
        result.failure.code,
      );
      return this.update({
        ...initialIdentityState,
        providers: this.#state.providers,
        busy: true,
        stage: signedOut ? "signedOut" : "unavailable",
        failure: signedOut ? null : result.failure,
      });
    }
    const session = result.value;
    const sameAccount = session.account.id === this.#state.session?.account.id;
    const selected = sameAccount ? this.#state.companyId : null;
    const companyId =
      session.companies.find((company) => company.id === selected)?.id ??
      session.companies[0]?.id ??
      null;
    this.clearPrivateCache();
    if (!sameAccount) this.#enrollmentId = null;
    this.update({ session, companyId, access: null, enrollment: null });
    if (session.assurance.required && !session.assurance.verified)
      return this.update({ stage: "challenge" });
    if (!companyId) return this.update({ stage: "ready" });
    const access = await this.useCases.loadCompanyAccess.execute(session, companyId, signal);
    if (signal.aborted) return;
    if (!access.ok) return this.update({ stage: "unavailable", failure: access.failure });
    this.update({ stage: "ready", access: access.value });
  }

  private async acceptVerification(
    verification: MfaVerification,
    signal: AbortSignal,
  ): Promise<void> {
    this.#enrollmentId = null;
    this.update({ enrollment: null });
    if (verification.recoveryCodes.length > 0) {
      this.update({ stage: "recoveryCodes", recoveryCodes: verification.recoveryCodes });
      return;
    }
    this.update({ stage: "loading" });
    await this.resolveSession(signal);
  }

  private async loadProviders(): Promise<void> {
    const controller = new AbortController();
    this.#providers = controller;
    try {
      const result = await this.useCases.loadProviders.execute(controller.signal);
      if (!controller.signal.aborted && result.ok) this.update({ providers: result.value });
    } catch {
      // Password sign-in remains available when optional provider discovery fails.
    } finally {
      if (this.#providers === controller) this.#providers = null;
    }
  }
}
