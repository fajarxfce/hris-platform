import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { OrganizationUnitChange } from "../../domain/entities/organization-unit-change";
import {
  canManageOrganization,
  organizationSaveWasRejected,
} from "../../domain/policies/organization-change-policy";
import type { OrganizationUseCases } from "../contracts/organization-use-cases";
import {
  initialOrganizationEditorState,
  type OrganizationEditorState,
} from "../models/organization-editor-state";

export type OrganizationEditableFields = Omit<OrganizationUnitChange, "id" | "expectedVersion">;
type Submission = Readonly<{ operation: OperationId; change: OrganizationUnitChange }>;

/** Owns one observed revision and one immutable submission until its receipt is known. */
export class OrganizationEditorController {
  #state = initialOrganizationEditorState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  readonly id: string;
  constructor(
    private readonly actions: Pick<OrganizationUseCases, "loadUnit" | "saveUnit">,
    private readonly access: CompanyAccess,
    readonly creating: boolean,
    id: string,
    private readonly nextIdentifier: () => string,
  ) {
    this.id = id;
  }
  getSnapshot = (): OrganizationEditorState => this.#state;
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
    this.publish(initialOrganizationEditorState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission !== null) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialOrganizationEditorState);
    if (!canManageOrganization(this.access.permissions)) {
      this.#pending = null;
      this.publish({
        ...initialOrganizationEditorState,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
      return;
    }
    if (this.creating) {
      this.#pending = null;
      this.publish({ ...initialOrganizationEditorState, stage: "editing" });
      return;
    }
    try {
      const result = await this.actions.loadUnit.execute(this.access, this.id, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish({
        ...initialOrganizationEditorState,
        ...(result.ok
          ? ({ stage: "editing", details: result.value } as const)
          : ({ stage: "unavailable", failure: result.failure } as const)),
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialOrganizationEditorState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: OrganizationEditableFields): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing") return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      change: Object.freeze({
        ...fields,
        id: this.id,
        expectedVersion: this.#state.details?.unit.version ?? null,
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
        result = await this.actions.saveUnit.execute(
          this.access,
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
      } else if (!wasUnconfirmed && organizationSaveWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: result.failure.code === "stale_version" ? "conflict" : "editing",
          failure: result.failure,
          operationId: null,
        });
      } else {
        this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: OrganizationEditorState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
