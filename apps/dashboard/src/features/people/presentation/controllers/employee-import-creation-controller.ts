import type { TextFile } from "../../../../core/domain/files/text-file";
import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeImportStart } from "../../domain/entities/employee-import-start";
import { employeeImportStartWasRejected } from "../../domain/policies/employee-import-file-policy";
import { canImportEmployees } from "../../domain/policies/employee-import-policy";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import {
  type EmployeeImportCreationState,
  initialEmployeeImportCreationState,
} from "../models/employee-import-creation-state";

type Submission = Readonly<{ operation: OperationId; input: EmployeeImportStart }>;
/** Owns one bounded CSV in memory; public state contains file metadata only. */
export class EmployeeImportCreationController {
  #state = initialEmployeeImportCreationState;
  #active = false;
  #pending: AbortController | null = null;
  #file: TextFile | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  private readonly id: string;
  constructor(
    private readonly actions: Pick<
      PeopleUseCases,
      "selectEmployeeImportFile" | "downloadEmployeeImportTemplate" | "startEmployeeImport"
    >,
    private readonly access: CompanyAccess,
    private readonly nextIdentifier: () => string,
  ) {
    this.id = nextIdentifier();
  }
  getSnapshot = (): EmployeeImportCreationState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    if (this.#active) return;
    this.#active = true;
    if (!canImportEmployees(this.access.permissions))
      this.publish({
        ...initialEmployeeImportCreationState,
        stage: "unavailable",
        failure: { code: "employee_import_access_required", fields: {}, parameters: {} },
      });
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.#file = null;
    this.#submission = null;
    this.publish(initialEmployeeImportCreationState);
  };
  selectFile = async (): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing") return;
    this.#file = null;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({
      ...this.#state,
      stage: "selecting",
      file: null,
      failure: null,
      templateRequested: false,
    });
    try {
      const result = await this.actions.selectEmployeeImportFile.execute(
        this.access,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#file = result.value;
        this.publish({
          ...this.#state,
          stage: "editing",
          file: result.value
            ? Object.freeze({ name: result.value.name, byteLength: result.value.byteLength })
            : null,
        });
      } else this.publish({ ...this.#state, stage: "editing", failure: result.failure });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...this.#state,
          stage: "editing",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  clearFile = (): void => {
    if (!this.#active || this.#state.stage !== "editing") return;
    this.#file = null;
    this.publish({ ...this.#state, file: null, failure: null });
  };
  downloadTemplate = async (): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing") return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...this.#state, stage: "downloading", failure: null, templateRequested: false });
    try {
      const result = await this.actions.downloadEmployeeImportTemplate.execute(
        this.access,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish({
        ...this.#state,
        stage: "editing",
        failure: result.ok ? null : result.failure,
        templateRequested: result.ok,
      });
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...this.#state,
          stage: "editing",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  start = async (reason: string): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing") return;
    if (!this.#file) {
      this.publish({
        ...this.#state,
        failure: { code: "employee_import_file_required", fields: {}, parameters: {} },
      });
      return;
    }
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({ id: this.id, file: this.#file, reason }),
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
      templateRequested: false,
    });
    try {
      let result: Result<MutationReceipt>;
      try {
        result = await this.actions.startEmployeeImport.execute(
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
        this.#file = null;
        this.#submission = null;
        this.publish({
          ...initialEmployeeImportCreationState,
          stage: "saved",
          receipt: result.value,
        });
      } else if (!wasUnconfirmed && employeeImportStartWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: "editing",
          operationId: null,
          failure: result.failure,
        });
      } else this.publish({ ...this.#state, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: EmployeeImportCreationState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
