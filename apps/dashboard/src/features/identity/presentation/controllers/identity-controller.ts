import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { Failure } from "../../../../core/domain/result";
import type { MfaVerification } from "../../domain/entities/mfa";
import type { CompanyAccess, Session } from "../../domain/entities/session";
import type { SignInCredentials } from "../../domain/entities/sign-in";
import type { IdentityUseCases } from "../contracts/identity-use-cases";
import { type IdentityState, initialIdentityState } from "../models/identity-state";
import { sameWorkspaceAccess, sameWorkspaceMemberships } from "../models/identity-workspace";

/** Owns screen requests and one-use secrets for one mounted application. */
export class IdentityController {
  #state: IdentityState = initialIdentityState;
  #listeners = new Set<() => void>();
  #active = false;
  #pending: AbortController | null = null;
  #providers: AbortController | null = null;
  #enrollmentId: OperationId | null = null;
  #workspaceRevision = 0;

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

  requestVerification = async (): Promise<void> => {
    if (this.#state.busy || this.#state.stage !== "ready") return;
    await this.runOwned(async (signal) => {
      this.update({ stage: "loading", access: null, verification: "requested" });
      await this.resolveSession(signal, true);
    });
  };

  cancelVerification = async (): Promise<void> => {
    if (this.#state.busy || this.#state.verification !== "requested") return;
    await this.refreshSession();
  };

  signIn = async (credentials: SignInCredentials): Promise<void> => {
    if (this.#state.busy || this.#state.stage !== "signedOut") return;
    await this.runOwned(async (signal) => {
      const result = await this.useCases.signIn.execute(credentials, signal);
      if (signal.aborted) return;
      if (!result.ok) return this.update({ failure: result.failure });
      this.clearPrivateCache();
      this.update({ stage: "loading", workspace: null });
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
    if (this.#state.stage === "ready" && companyId === this.#state.companyId) return;
    this.clearPrivateCache();
    await this.runOwned(async (signal) => {
      this.update({
        stage: "loading",
        companyId,
        access: null,
        workspace: null,
        verification: null,
      });
      const access = await this.useCases.loadCompanyAccess.execute(session, companyId, signal);
      if (signal.aborted) return;
      if (!access.ok) return this.update({ stage: "unavailable", failure: access.failure });
      this.acceptWorkspace(session, companyId, access.value);
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
        return this.reportVerificationFailure(result.failure);
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
        return this.reportVerificationFailure(result.failure);
      }
      await this.acceptVerification(result.value, signal);
    });
  };

  verifyMfa = async (code: string, recovery: boolean): Promise<void> => {
    if (this.#state.busy || this.#state.stage !== "challenge") return;
    await this.runOwned(async (signal) => {
      const result = await this.useCases.verifyMfa.execute(code, recovery, signal);
      if (signal.aborted) return;
      if (!result.ok) return this.reportVerificationFailure(result.failure);
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

  private reportVerificationFailure(failure: Failure): void {
    if (!["authentication_required", "session_revoked", "unauthenticated"].includes(failure.code)) {
      this.update({ failure });
      return;
    }
    this.#enrollmentId = null;
    this.clearPrivateCache();
    this.update({
      ...initialIdentityState,
      stage: "signedOut",
      busy: true,
      providers: this.#state.providers,
    });
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

  private async resolveSession(signal: AbortSignal, requestVerification = false): Promise<void> {
    const result = await this.useCases.loadSession.execute(signal);
    if (signal.aborted) return;
    if (!result.ok) {
      const signedOut = ["authentication_required", "session_revoked", "unauthenticated"].includes(
        result.failure.code,
      );
      if (!signedOut)
        return this.update({ stage: "unavailable", access: null, failure: result.failure });
      this.clearPrivateCache();
      return this.update({
        ...initialIdentityState,
        providers: this.#state.providers,
        busy: true,
        stage: "signedOut",
        failure: null,
      });
    }
    const session = result.value;
    const sameAccount = session.account.id === this.#state.session?.account.id;
    if (!sameAccount) {
      this.#enrollmentId = null;
      this.clearPrivateCache();
      this.update({ workspace: null });
    }
    // The API deliberately redacts memberships and grants until MFA succeeds.
    // Keep the former scope hidden; only a complete post-verification read may restore it.
    if (session.assurance.required && !session.assurance.verified) {
      this.update({
        session,
        access: null,
        enrollment: null,
        stage: "challenge",
        verification: "required",
        ...(sameAccount ? {} : { companyId: null }),
      });
      return;
    }
    const selected = sameAccount ? this.#state.companyId : null;
    const companyId =
      session.companies.find((company) => company.id === selected)?.id ??
      session.companies[0]?.id ??
      null;
    const workspace = this.#state.workspace;
    const retain = workspace !== null && sameWorkspaceMemberships(workspace, session, companyId);
    if (!retain) this.clearPrivateCache();
    this.update({
      session,
      companyId,
      access: null,
      enrollment: null,
      workspace: retain ? workspace : null,
    });
    if (requestVerification) return this.update({ stage: "challenge", verification: "requested" });
    if (!companyId) return this.acceptWorkspace(session, companyId, null);
    const access = await this.useCases.loadCompanyAccess.execute(session, companyId, signal);
    if (signal.aborted) return;
    if (!access.ok) {
      if (
        ["mfa_required", "mfa_setup_required", "recent_authentication_required"].includes(
          access.failure.code,
        )
      )
        return this.update({ stage: "challenge", verification: "required", failure: null });
      const revoked = ["authentication_required", "session_revoked", "unauthenticated"].includes(
        access.failure.code,
      );
      const denied = ["access_denied", "company_access_denied"].includes(access.failure.code);
      if (revoked || denied) this.clearPrivateCache();
      if (revoked)
        return this.update({
          ...initialIdentityState,
          stage: "signedOut",
          busy: true,
          providers: this.#state.providers,
        });
      return this.update({
        stage: "unavailable",
        failure: access.failure,
        ...(denied ? { workspace: null } : {}),
      });
    }
    this.acceptWorkspace(session, companyId, access.value);
  }

  private acceptWorkspace(
    session: Session,
    companyId: CompanyId | null,
    access: CompanyAccess | null,
  ): void {
    const previous = this.#state.workspace;
    const retained =
      previous !== null &&
      sameWorkspaceMemberships(previous, session, companyId) &&
      sameWorkspaceAccess(previous.access, access);
    if (!retained) this.clearPrivateCache();
    const workspace = retained
      ? previous
      : Object.freeze({
          revision: ++this.#workspaceRevision,
          accountId: session.account.id,
          company: session.companies.find((company) => company.id === companyId) ?? null,
          companies: session.companies,
          platformPermissions: session.permissions,
          access,
        });
    this.update({
      stage: "ready",
      session,
      companyId,
      workspace,
      access: workspace.access,
      verification: null,
    });
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
