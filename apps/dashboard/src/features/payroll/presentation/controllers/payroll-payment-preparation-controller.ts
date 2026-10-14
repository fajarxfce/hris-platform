import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { type Failure, failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollPayable } from "../../domain/entities/payroll-payable";
import type { PayrollPaymentDestination } from "../../domain/entities/payroll-payment-destination";
import type { PayrollPaymentPreparation } from "../../domain/entities/payroll-payment-preparation";
import { payrollPaymentEvidenceUnavailable } from "../../domain/policies/payroll-payment-failure-policy";
import {
  addPayrollPaymentSelection,
  payrollPaymentPreparationWasRejected,
  preparePayrollPaymentDestination,
  reviewPayrollPaymentPreparation,
} from "../../domain/policies/payroll-payment-preparation-policy";
import { canReadPayrollPayments } from "../../domain/policies/payroll-payment-read-policy";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialPayrollPaymentPreparationState,
  type PayrollPaymentPreparationState,
} from "../models/payroll-payment-preparation-state";

type Command = Readonly<{
  operation: OperationId;
  id: string;
  review: PayrollPaymentPreparation;
  itemIds: readonly string[];
}>;
export class PayrollPaymentPreparationController {
  #state = initialPayrollPaymentPreparationState;
  #active = false;
  #pending: AbortController | null = null;
  #command: Command | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly prepare: PayrollUseCases["preparePayment"],
    private readonly access: CompanyAccess,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): PayrollPaymentPreparationState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    if (this.#active) return;
    this.#active = true;
    this.restart();
  };
  deactivate = (): void => {
    this.#active = false;
    this.#pending?.abort();
    this.#pending = null;
    this.#command = null;
    this.publish(initialPayrollPaymentPreparationState);
  };
  restart = (): void => {
    if (!this.#active || this.#command) return;
    this.publish(
      canReadPayrollPayments(this.access.permissions)
        ? { ...initialPayrollPaymentPreparationState, stage: "editing" }
        : {
            ...initialPayrollPaymentPreparationState,
            stage: "unavailable",
            failure: { code: "access_denied", fields: {}, parameters: {} },
          },
    );
  };
  openPayables = (): void => {
    if (!this.#active || this.#state.stage !== "editing" || this.#state.items.length >= 100) return;
    this.publish({ ...this.#state, panel: { kind: "payables" }, entryFailure: null });
  };
  closePanel = (): void => {
    if (!this.#active || this.#state.stage !== "editing") return;
    this.publish({ ...this.#state, panel: { kind: "closed" }, entryFailure: null });
  };
  addPayable = (payable: PayrollPayable): void => {
    if (!this.#active || this.#state.stage !== "editing" || this.#state.panel.kind !== "payables")
      return;
    const result = addPayrollPaymentSelection(this.access, this.#state.items, payable);
    this.publish(
      result.ok
        ? { ...this.#state, items: result.value, failure: null, entryFailure: null }
        : { ...this.#state, entryFailure: result.failure },
    );
  };
  selectDestination = (assessmentId: string): void => {
    if (
      !this.#active ||
      this.#state.stage !== "editing" ||
      !this.#state.items.some((item) => item.payable.assessmentId === assessmentId)
    )
      return;
    this.publish({
      ...this.#state,
      panel: { kind: "destination", assessmentId, dirty: false },
      entryFailure: null,
    });
  };
  setDestinationDirty = (assessmentId: string, dirty: boolean): void => {
    const panel = this.#state.panel;
    if (
      !this.#active ||
      this.#state.stage !== "editing" ||
      panel.kind !== "destination" ||
      panel.assessmentId !== assessmentId ||
      panel.dirty === dirty
    )
      return;
    this.publish({ ...this.#state, panel: { ...panel, dirty } });
  };
  saveDestination = (input: PayrollPaymentDestination): void => {
    const panel = this.#state.panel;
    if (!this.#active || this.#state.stage !== "editing" || panel.kind !== "destination") return;
    const destination = preparePayrollPaymentDestination(input);
    if (!destination.ok) {
      this.publish({ ...this.#state, entryFailure: destination.failure });
      return;
    }
    this.publish({
      ...this.#state,
      items: Object.freeze(
        this.#state.items.map((item) =>
          item.payable.assessmentId === panel.assessmentId
            ? Object.freeze({ ...item, destination: destination.value })
            : item,
        ),
      ),
      panel: { kind: "closed" },
      entryFailure: null,
      failure: null,
    });
  };
  removeSelected = (): void => {
    const panel = this.#state.panel;
    if (!this.#active || this.#state.stage !== "editing" || panel.kind !== "destination") return;
    this.publish({
      ...this.#state,
      items: Object.freeze(
        this.#state.items.filter((item) => item.payable.assessmentId !== panel.assessmentId),
      ),
      panel: { kind: "closed" },
      entryFailure: null,
      failure: null,
    });
  };
  review = (title: string, reason: string): void => {
    if (!this.#active || this.#state.stage !== "editing" || this.#state.panel.kind !== "closed")
      return;
    const review = reviewPayrollPaymentPreparation(this.access, this.#state.items, title, reason);
    this.publish(
      review.ok
        ? { ...this.#state, stage: "reviewing", review: review.value, failure: null }
        : { ...this.#state, failure: review.failure },
    );
  };
  edit = (): void => {
    if (!this.#active || this.#state.stage !== "reviewing") return;
    this.publish({ ...this.#state, stage: "editing", review: null, failure: null });
  };
  withdrawEvidence = (failure: Failure): void => {
    if (
      !this.#active ||
      this.#state.stage !== "editing" ||
      !payrollPaymentEvidenceUnavailable(failure)
    )
      return;
    this.publish({ ...initialPayrollPaymentPreparationState, stage: "unavailable", failure });
  };
  confirm = async (): Promise<void> => {
    if (!this.#active || this.#state.stage !== "reviewing" || !this.#state.review) return;
    this.#command = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      id: this.nextIdentifier(),
      review: this.#state.review,
      itemIds: Object.freeze(this.#state.review.items.map(() => this.nextIdentifier())),
    });
    await this.send(false);
  };
  retry = async (): Promise<void> => {
    if (this.#active && this.#state.stage === "unconfirmed") await this.send(true);
  };
  private async send(wasUnconfirmed: boolean): Promise<void> {
    const command = this.#command;
    if (!command) return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({
      ...this.#state,
      stage: "submitting",
      operationId: command.operation,
      failure: null,
    });
    try {
      let result: Result<MutationReceipt>;
      try {
        result = await this.prepare.execute(
          this.access,
          command.operation,
          command.id,
          command.review,
          command.itemIds,
          pending.signal,
        );
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#command = null;
        this.publish({
          ...initialPayrollPaymentPreparationState,
          stage: "saved",
          receipt: result.value,
        });
        return;
      }
      const unavailable = payrollPaymentEvidenceUnavailable(result.failure);
      const retained = unavailable ? { ...this.#state, items: [], review: null } : this.#state;
      if (!wasUnconfirmed && payrollPaymentPreparationWasRejected(result.failure)) {
        this.#command = null;
        this.publish({
          ...retained,
          stage: unavailable ? "unavailable" : "editing",
          operationId: null,
          review: null,
          failure: result.failure,
        });
      } else this.publish({ ...retained, stage: "unconfirmed", failure: result.failure });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: PayrollPaymentPreparationState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
