import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { EmployeeId } from "../../domain/entities/employee";
import type { AssignedEmploymentTerms } from "../../domain/entities/employment-assignments";
import type { EmploymentTransferInput } from "../../domain/entities/employment-transfer-input";
import {
  canTransferEmployment,
  employmentTransferWasRejected,
} from "../../domain/policies/employment-transfer-policy";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import {
  type EmploymentTransferState,
  initialEmploymentTransferState,
} from "../models/employment-transfer-state";

export type TransferEditableFields = Readonly<{
  employeeNumber: string;
  reason: string;
  terms: Omit<AssignedEmploymentTerms, "startDate" | "effectiveFrom">;
}>;
type Submission = Readonly<{ operation: OperationId; input: EmploymentTransferInput }>;

export class EmploymentTransferController {
  #state = initialEmploymentTransferState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      PeopleUseCases,
      "loadEmploymentTransferReview" | "transferEmployee"
    >,
    private readonly access: CompanyAccess,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): EmploymentTransferState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    if (this.#active) return;
    this.#active = true;
    if (!canTransferEmployment(this.access.permissions))
      this.publish({
        ...initialEmploymentTransferState,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.#submission = null;
    this.publish(initialEmploymentTransferState);
  };
  selectTarget = async (targetCompanyId: string): Promise<void> => {
    if (
      !this.#active ||
      this.#submission ||
      this.#state.stage === "saved" ||
      !canTransferEmployment(this.access.permissions)
    )
      return;
    this.#pending?.abort();
    this.#pending = null;
    this.publish({ ...initialEmploymentTransferState, targetCompanyId });
    if (!targetCompanyId) return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...this.#state, stage: "loading" });
    try {
      const result = await this.actions.loadEmploymentTransferReview.execute(
        this.access,
        this.id,
        targetCompanyId,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...this.#state, stage: "editing", review: result.value }
          : { ...this.#state, stage: "unavailable", failure: result.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...this.#state,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  refresh = (): Promise<void> => this.selectTarget(this.#state.targetCompanyId);
  requestTransfer = (fields: TransferEditableFields): void => {
    const review = this.#state.review;
    if (!this.#active || this.#state.stage !== "editing" || !review) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: Object.freeze({
        employeeId: review.employee.id,
        expectedVersion: review.employee.version,
        targetCompanyId: review.targetCompany.id,
        targetEmploymentId: this.nextIdentifier() as EmployeeId,
        employeeNumber: fields.employeeNumber,
        reason: fields.reason,
        terms: Object.freeze({
          ...fields.terms,
          startDate: review.targetDate,
          effectiveFrom: review.targetDate,
        }),
      }),
    });
    this.publish({ ...this.#state, stage: "confirming", failure: null });
  };
  dismissTransfer = (): void => {
    if (!this.#active || this.#state.stage !== "confirming") return;
    this.#submission = null;
    this.publish({ ...this.#state, stage: "editing" });
  };
  confirmTransfer = async (): Promise<void> => {
    if (this.#active && this.#state.stage === "confirming") await this.submit(false);
  };
  retryTransfer = async (): Promise<void> => {
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
        result = await this.actions.transferEmployee.execute(
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
      } else if (!wasUnconfirmed && employmentTransferWasRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: [
            "stale_version",
            "transfer_requires_current_company_date",
            "source_employment_not_transferable",
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
  private publish(state: EmploymentTransferState): void {
    this.#state = Object.freeze(state);
    for (const listener of this.#listeners) listener();
  }
}
