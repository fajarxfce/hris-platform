import type { AccountId, OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LoadCompanyMembers } from "../../../identity/domain/usecases/load-company-members";
import type { PersonAccountBinding } from "../../domain/entities/person-account-binding";
import {
  accountBindingWasRejected,
  canLinkPersonAccount,
  canLinkPersonAccounts,
  isAccountBindingCandidate,
} from "../../domain/policies/person-account-binding-policy";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import {
  initialPersonAccountBindingState,
  type PersonAccountBindingState,
} from "../models/person-account-binding-state";

type Submission = Readonly<{ operation: OperationId; binding: PersonAccountBinding }>;
export class PersonAccountBindingController {
  #state = initialPersonAccountBindingState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly people: Pick<PeopleUseCases, "loadPersonProfile" | "bindPersonAccount">,
    private readonly loadMembers: Pick<LoadCompanyMembers, "execute">,
    private readonly access: CompanyAccess,
    private readonly authorId: AccountId,
    private readonly employeeId: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): PersonAccountBindingState => this.#state;
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
    this.publish(initialPersonAccountBindingState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...initialPersonAccountBindingState, stage: "loading" });
    if (!canLinkPersonAccounts(this.access.permissions)) {
      this.#pending = null;
      this.publish({
        ...initialPersonAccountBindingState,
        stage: "unavailable",
        failure: { code: "person_account_link_access_required", fields: {}, parameters: {} },
      });
      return;
    }
    try {
      const result = await this.people.loadPersonProfile.execute(
        this.access,
        this.employeeId,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (!result.ok)
        this.publish({
          ...initialPersonAccountBindingState,
          stage: "unavailable",
          failure: result.failure,
        });
      else if (!canLinkPersonAccount(this.access, result.value))
        this.publish({
          ...initialPersonAccountBindingState,
          stage: "blocked",
          profile: result.value,
          failure: {
            code:
              result.value.accountId !== null
                ? "person_account_already_bound"
                : "profile_owner_required",
            fields: {},
            parameters: {},
          },
        });
      else {
        this.publish({
          ...initialPersonAccountBindingState,
          stage: "editing",
          profile: result.value,
        });
        await this.loadCandidates(null);
      }
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialPersonAccountBindingState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  loadCandidates = async (after: string | null): Promise<void> => {
    if (
      !this.#active ||
      this.#submission ||
      this.#state.stage !== "editing" ||
      (after !== null && after !== this.#state.candidateNext)
    )
      return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({
      ...this.#state,
      candidates: [],
      candidateAfter: after,
      candidateNext: null,
      loadingCandidates: true,
      failure: null,
    });
    try {
      const result = await this.loadMembers.execute(this.access, after, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (!result.ok)
        this.publish({
          ...initialPersonAccountBindingState,
          stage: "unavailable",
          failure: result.failure,
        });
      else
        this.publish({
          ...this.#state,
          candidates: Object.freeze(
            result.value.items.filter((member) => isAccountBindingCandidate(member, this.authorId)),
          ),
          candidateNext: result.value.nextCursor,
          loadingCandidates: false,
        });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialPersonAccountBindingState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  selectAccount = (id: string): void => {
    if (this.#state.stage !== "editing" || this.#state.loadingCandidates) return;
    const selected = this.#state.candidates.find((member) => member.id === id);
    if (selected && isAccountBindingCandidate(selected, this.authorId))
      this.publish({ ...this.#state, selected, failure: null });
  };
  bind = async (reason: string): Promise<void> => {
    const { profile, selected, stage, loadingCandidates } = this.#state;
    if (!this.#active || stage !== "editing" || loadingCandidates || !profile || !selected) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      binding: Object.freeze({
        personId: profile.personId,
        ownerCompanyId: profile.ownerCompanyId,
        accountId: selected.id,
        expectedVersion: profile.version,
        reason,
      }),
    });
    await this.submit(false);
  };
  retry = async (): Promise<void> => {
    if (this.#active && this.#state.stage === "unconfirmed") await this.submit(true);
  };
  private async submit(wasUnconfirmed: boolean): Promise<void> {
    const submission = this.#submission;
    if (!submission) return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({
      ...this.#state,
      stage: "submitting",
      failure: null,
      operationId: submission.operation,
    });
    try {
      let result: Result<MutationReceipt>;
      try {
        result = await this.people.bindPersonAccount.execute(
          this.access,
          this.authorId,
          this.employeeId,
          submission.operation,
          submission.binding,
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
          stage: "bound",
          failure: null,
          operationId: null,
          receipt: result.value,
        });
      } else if (!wasUnconfirmed && accountBindingWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: [
            "stale_version",
            "person_account_already_bound",
            "account_membership_required",
            "data_conflict",
          ].includes(result.failure.code)
            ? "conflict"
            : "editing",
          failure: result.failure,
          operationId: null,
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: PersonAccountBindingState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
