import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../../domain/entities/employee";
import type { EmployeeCreation } from "../../domain/entities/employee-creation";
import type { PersonId } from "../../domain/entities/person-profile";
import {
  canCreateEmployee,
  employeeCreationWasRejected,
} from "../../domain/policies/employee-creation-policy";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import {
  type EmployeeCreationState,
  initialEmployeeCreationState,
} from "../models/employee-creation-state";

export type EmployeeCreationFields = Omit<EmployeeCreation, "employeeId" | "personId">;
type Submission = Readonly<{ operation: OperationId; input: EmployeeCreation }>;

/** Owns the identifiers and one immutable command until its outcome is known. */
export class EmployeeCreationController {
  #state = initialEmployeeCreationState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  readonly employeeId: EmployeeId;
  private readonly personId: PersonId;
  constructor(
    private readonly create: PeopleUseCases["createEmployee"],
    private readonly access: CompanyAccess,
    private readonly nextIdentifier: () => string,
  ) {
    this.employeeId = nextIdentifier() as EmployeeId;
    this.personId = nextIdentifier() as PersonId;
  }
  getSnapshot = (): EmployeeCreationState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    if (this.#active) return;
    this.#active = true;
    if (!canCreateEmployee(this.access.permissions))
      this.publish({
        ...initialEmployeeCreationState,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.#submission = null;
    this.publish(initialEmployeeCreationState);
  };
  save = async (fields: EmployeeCreationFields): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing") return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({ ...fields, employeeId: this.employeeId, personId: this.personId }),
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
        result = await this.create.execute(
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
          ...initialEmployeeCreationState,
          stage: "saved",
          receipt: result.value,
          startDate: submission.input.startDate,
        });
      } else if (!wasUnconfirmed && employeeCreationWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({ ...initialEmployeeCreationState, failure: result.failure });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: EmployeeCreationState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
