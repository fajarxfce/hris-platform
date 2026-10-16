import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyMembershipChange } from "../../domain/entities/company-membership-change";
import type { CompanyAccess } from "../../domain/entities/session";
import { companyMembershipSaveWasRejected } from "../../domain/policies/company-membership-change-policy";
import type { IdentityAdministrationUseCases } from "../contracts/identity-administration-use-cases";
import {
  type CompanyMembershipEditorState,
  initialCompanyMembershipEditorState,
} from "../models/company-membership-editor-state";

export type CompanyMembershipFields = Omit<
  CompanyMembershipChange,
  "accountId" | "expectedVersion"
>;
type Submission = Readonly<{ operation: OperationId; input: CompanyMembershipChange }>;

export class CompanyMembershipEditorController {
  #state = initialCompanyMembershipEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      IdentityAdministrationUseCases,
      "loadCompanyMember" | "loadAssignablePermissions" | "saveCompanyMembership"
    >,
    private readonly access: CompanyAccess,
    private readonly memberId: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): CompanyMembershipEditorState => this.#state;
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
    this.publish(initialCompanyMembershipEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialCompanyMembershipEditorState);
    try {
      const [permissions, grant] = await Promise.all([
        this.actions.loadAssignablePermissions.execute(this.access, pending.signal),
        this.actions.loadCompanyMember.execute(this.access, this.memberId, pending.signal),
      ]);
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (!permissions.ok || !grant.ok) {
        this.publish({
          ...initialCompanyMembershipEditorState,
          stage: "unavailable",
          failure: !permissions.ok ? permissions.failure : !grant.ok ? grant.failure : null,
        });
        return;
      }
      this.publish({
        ...initialCompanyMembershipEditorState,
        stage: "editing",
        grant: grant.value,
        permissions: permissions.value,
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending) {
        pending.abort();
        this.publish({
          ...initialCompanyMembershipEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: CompanyMembershipFields): Promise<void> => {
    const grant = this.#state.grant;
    if (!this.#active || this.#state.stage !== "editing" || !grant) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        ...fields,
        accountId: grant.member.id,
        expectedVersion: grant.member.version,
        permissions: Object.freeze([...fields.permissions]),
        roleTemplates: Object.freeze(
          fields.roleTemplates.map((role) =>
            Object.freeze({
              id: role.id,
              version: role.version,
              retainApplied: role.retainApplied,
            }),
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
        result = await this.actions.saveCompanyMembership.execute(
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
        this.publish({
          ...initialCompanyMembershipEditorState,
          stage: "saved",
          receipt: result.value,
        });
      } else if (!wasUnconfirmed && companyMembershipSaveWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          failure: result.failure,
          operationId: null,
          stage: ["stale_version", "company_member_not_found"].includes(result.failure.code)
            ? "conflict"
            : "editing",
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: CompanyMembershipEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
