import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { type Failure, failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  PersonProfileChange,
  PersonProfileFields,
} from "../../domain/entities/person-profile";
import {
  canManagePersonProfile,
  canReadPersonProfile,
  profileSaveWasRejected,
} from "../../domain/policies/person-profile-policy";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { initialPersonProfileState, type PersonProfileState } from "../models/person-profile-state";

export type ProfileEditableFields = PersonProfileFields & Readonly<{ reason: string }>;
type Submission = Readonly<{ operation: OperationId; change: PersonProfileChange }>;

/** Owns a current private profile; editing preserves one observed version and immutable command. */
export class PersonProfileController {
  #state = initialPersonProfileState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<PeopleUseCases, "loadPersonProfile" | "savePersonProfile">,
    private readonly access: CompanyAccess,
    private readonly employeeId: string,
    readonly mode: "read" | "edit",
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): PersonProfileState => this.#state;
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
    this.publish(initialPersonProfileState);
  };
  reportScopeFailure = (failure: Failure): void => {
    if (
      this.mode !== "read" ||
      !this.#active ||
      ![
        "access_denied",
        "person_profile_not_found",
        "company_access_denied",
        "authentication_required",
        "session_revoked",
        "unauthenticated",
        "mfa_required",
        "mfa_setup_required",
      ].includes(failure.code)
    )
      return;
    this.#pending?.abort();
    this.#pending = null;
    this.#submission = null;
    this.publish({ ...initialPersonProfileState, stage: "unavailable", failure });
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission !== null) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialPersonProfileState);
    if (
      this.mode === "edit" &&
      (!canReadPersonProfile(this.access.permissions) ||
        !this.access.permissions.includes("people.profile.manage"))
    ) {
      this.#pending = null;
      this.publish({
        ...initialPersonProfileState,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
      return;
    }
    try {
      const result = await this.actions.loadPersonProfile.execute(
        this.access,
        this.employeeId,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (!result.ok)
        this.publish({
          ...initialPersonProfileState,
          stage: "unavailable",
          failure: result.failure,
        });
      else if (this.mode === "edit" && !canManagePersonProfile(this.access, result.value))
        this.publish({
          ...initialPersonProfileState,
          stage: "unavailable",
          failure: { code: "profile_owner_required", fields: {}, parameters: {} },
        });
      else
        this.publish({
          ...initialPersonProfileState,
          stage: this.mode === "edit" ? "editing" : "ready",
          profile: result.value,
        });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialPersonProfileState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: ProfileEditableFields): Promise<void> => {
    const profile = this.#state.profile;
    if (!this.#active || this.#state.stage !== "editing" || !profile) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      change: Object.freeze({
        ...fields,
        personId: profile.personId,
        ownerCompanyId: profile.ownerCompanyId,
        expectedVersion: profile.version,
      }),
    });
    await this.submit(false);
  };
  retrySave = async (): Promise<void> => {
    if (!this.#active || this.#state.stage !== "unconfirmed") return;
    await this.submit(true);
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
        result = await this.actions.savePersonProfile.execute(
          this.access,
          this.employeeId,
          submission.operation,
          submission.change,
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
          failure: null,
          receipt: result.value,
          operationId: null,
        });
      } else if (!wasUnconfirmed && profileSaveWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: result.failure.code === "stale_version" ? "conflict" : "editing",
          failure: result.failure,
          operationId: null,
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: PersonProfileState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
