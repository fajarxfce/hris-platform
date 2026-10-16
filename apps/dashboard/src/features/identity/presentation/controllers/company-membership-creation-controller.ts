import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccountAccess } from "../../domain/entities/company-account-access";
import type { CompanyMembershipAssignment } from "../../domain/entities/company-membership-assignment";
import { companyMembershipSaveWasRejected } from "../../domain/policies/company-membership-change-policy";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import {
  type CompanyMembershipCreationState,
  initialCompanyMembershipCreationState,
} from "../models/company-membership-creation-state";

type Submission = Readonly<{ operation: OperationId; input: CompanyMembershipAssignment }>;

export class CompanyMembershipCreationController {
  #state = initialCompanyMembershipCreationState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      IdentityAdministrationUseCases,
      "prepareCompanyMembership" | "createCompanyMembership"
    >,
    private readonly access: CompanyAccountAccess,
    private readonly accountId: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): CompanyMembershipCreationState => this.#state;
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
    this.publish(initialCompanyMembershipCreationState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialCompanyMembershipCreationState);
    try {
      const result = await this.actions.prepareCompanyMembership.execute(
        this.access.company,
        this.access.platform,
        this.accountId,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialCompanyMembershipCreationState, stage: "editing", candidate: result.value }
          : {
              ...initialCompanyMembershipCreationState,
              stage: "unavailable",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialCompanyMembershipCreationState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: Omit<CompanyMembershipAssignment, "accountId">): Promise<void> => {
    const candidate = this.#state.candidate;
    if (!this.#active || this.#state.stage !== "editing" || !candidate) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        accountId: candidate.accountId,
        reason: fields.reason,
        permissions: Object.freeze([...fields.permissions]),
        roleTemplates: Object.freeze(
          fields.roleTemplates.map((role) =>
            Object.freeze({ id: role.id, version: role.version, retainApplied: false }),
          ),
        ),
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
        result = await this.actions.createCompanyMembership.execute(
          this.access.company,
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
        this.publish({
          ...initialCompanyMembershipCreationState,
          stage: "saved",
          receipt: result.value,
        });
      } else if (!wasUnconfirmed && companyMembershipSaveWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          failure: result.failure,
          operationId: null,
          stage: ["stale_version", "account_unavailable", "company_member_not_found"].includes(
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
  private publish(state: CompanyMembershipCreationState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
