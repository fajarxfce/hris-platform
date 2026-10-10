import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LoadEmployee } from "../../../people/domain/usecases/load-employee";
import type { LifecycleCaseStart } from "../../domain/entities/lifecycle-case-start";
import type { LifecycleTemplate } from "../../domain/entities/lifecycle-template";
import {
  canPrepareLifecycleCase,
  lifecycleCaseStartWasRejected,
} from "../../domain/policies/lifecycle-case-start-policy";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import {
  initialLifecycleCaseCreationState,
  type LifecycleCaseCreationState,
} from "../models/lifecycle-case-creation-state";

export type LifecycleCaseCreationFields = Readonly<{
  template: LifecycleTemplate | null;
  targetDate: string;
  reason: string;
}>;
type Submission = Readonly<{ operation: OperationId; input: LifecycleCaseStart }>;

/** Owns the authorized employee read and one case command until acknowledgement or disposal. */
export class LifecycleCaseCreationController {
  #state = initialLifecycleCaseCreationState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  readonly caseId: string;
  constructor(
    private readonly loadEmployee: Pick<LoadEmployee, "execute">,
    private readonly start: LifecycleUseCases["startCase"],
    private readonly access: CompanyAccess,
    private readonly employeeId: string,
    private readonly asOf: string,
    private readonly nextIdentifier: () => string,
  ) {
    this.caseId = nextIdentifier();
  }
  getSnapshot = (): LifecycleCaseCreationState => this.#state;
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
    this.publish(initialLifecycleCaseCreationState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || !["loading", "unavailable"].includes(this.#state.stage)) return;
    this.#pending?.abort();
    this.#pending = null;
    this.publish(initialLifecycleCaseCreationState);
    if (!canPrepareLifecycleCase(this.access.permissions)) {
      this.publish({
        ...initialLifecycleCaseCreationState,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
      return;
    }
    const pending = new AbortController();
    this.#pending = pending;
    try {
      const result = await this.loadEmployee.execute(
        this.access,
        this.employeeId,
        this.asOf,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialLifecycleCaseCreationState, stage: "editing", employee: result.value }
          : { ...initialLifecycleCaseCreationState, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialLifecycleCaseCreationState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  save = async (fields: LifecycleCaseCreationFields): Promise<void> => {
    const employee = this.#state.employee;
    if (!this.#active || this.#state.stage !== "editing" || !employee) return;
    const template = fields.template;
    if (!template || template.companyId !== this.access.companyId || !template.active) {
      this.publish({
        ...this.#state,
        failure: { code: "lifecycle_template_unavailable", fields: {}, parameters: {} },
      });
      return;
    }
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        id: this.caseId,
        employmentId: employee.id,
        templateId: template.id,
        templateVersion: template.version,
        targetDate: fields.targetDate,
        assignees: Object.freeze({}),
        reason: fields.reason,
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
      stage: "saving",
      failure: null,
      operationId: submission.operation,
    });
    try {
      let result: Result<MutationReceipt>;
      try {
        result = await this.start.execute(
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
          ...this.#state,
          stage: "saved",
          receipt: result.value,
          operationId: null,
          failure: null,
        });
      } else if (!wasUnconfirmed && lifecycleCaseStartWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: "editing",
          failure: result.failure,
          operationId: null,
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: LifecycleCaseCreationState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
