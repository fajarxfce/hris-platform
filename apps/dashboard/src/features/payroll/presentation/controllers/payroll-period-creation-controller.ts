import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  PayrollPeriodCreation,
  PayrollPeriodFields,
} from "../../domain/entities/payroll-period-creation";
import {
  canPreparePayrollPeriod,
  payrollPeriodAccessLost,
  payrollPeriodCommandRejected,
} from "../../domain/policies/payroll-period-policy";
import type { PayrollUseCases } from "../contracts/payroll-use-cases";
import {
  initialPayrollPeriodCreationState,
  type PayrollPeriodCreationState,
} from "../models/payroll-period-creation-state";

type Submission = Readonly<{ operation: OperationId; input: PayrollPeriodCreation }>;
export class PayrollPeriodCreationController {
  #state = initialPayrollPeriodCreationState;
  #active = false;
  #search: AbortController | null = null;
  #save: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly actions: Pick<
      PayrollUseCases,
      "listPeriodEmployees" | "reviewPeriodCreation" | "createPeriod"
    >,
    private readonly access: CompanyAccess,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): PayrollPeriodCreationState => this.#state;
  subscribe = (listener: () => void): (() => void) => {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  };
  activate = (): void => {
    if (this.#active) return;
    this.#active = true;
    if (!canPreparePayrollPeriod(this.access.permissions)) {
      this.publish({
        ...initialPayrollPeriodCreationState,
        stage: "unavailable",
        failure: { code: "access_denied", fields: {}, parameters: {} },
      });
      return;
    }
    void this.search("", null);
  };
  deactivate = (): void => {
    this.#active = false;
    this.#search?.abort();
    this.#save?.abort();
    this.#search = null;
    this.#save = null;
    this.#submission = null;
    this.publish(initialPayrollPeriodCreationState);
  };
  search = async (query: string, after: string | null): Promise<void> => {
    if (!this.#active || this.#state.stage !== "editing") return;
    this.#search?.abort();
    const pending = new AbortController();
    this.#search = pending;
    this.publish({ ...this.#state, page: null, query, after, loading: true, searchFailure: null });
    try {
      const result = await this.actions.listPeriodEmployees.execute(
        this.access,
        query,
        after,
        pending.signal,
      );
      if (pending.signal.aborted || this.#search !== pending) return;
      if (result.ok) this.publish({ ...this.#state, page: result.value, loading: false });
      else if (payrollPeriodAccessLost(result.failure))
        this.publish({
          ...initialPayrollPeriodCreationState,
          stage: "unavailable",
          failure: result.failure,
        });
      else
        this.publish({ ...this.#state, page: null, loading: false, searchFailure: result.failure });
    } catch {
      if (!pending.signal.aborted && this.#search === pending)
        this.publish({
          ...this.#state,
          loading: false,
          searchFailure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#search === pending) this.#search = null;
    }
  };
  toggle = (id: string, checked: boolean): void => {
    if (!this.#active || this.#state.stage !== "editing") return;
    if (!checked) {
      this.remove(id);
      return;
    }
    const employee = this.#state.page?.items.find((row) => row.id === id);
    if (!employee || this.#state.loading || this.#state.selected.some((row) => row.id === id))
      return;
    if (this.#state.selected.length >= 5000) {
      this.publish({
        ...this.#state,
        failure: { code: "invalid_payroll_period", fields: {}, parameters: {} },
      });
      return;
    }
    this.publish({
      ...this.#state,
      selected: Object.freeze([...this.#state.selected, Object.freeze({ ...employee })]),
      failure: null,
    });
  };
  togglePage = (checked: boolean): void => {
    if (
      !this.#active ||
      this.#state.stage !== "editing" ||
      this.#state.loading ||
      !this.#state.page
    )
      return;
    const visible = new Set(this.#state.page.items.map((row) => row.id)),
      current = new Set(this.#state.selected.map((row) => row.id));
    const selected = checked
      ? [
          ...this.#state.selected,
          ...this.#state.page.items
            .filter((row) => !current.has(row.id))
            .map((row) => Object.freeze({ ...row })),
        ]
      : this.#state.selected.filter((row) => !visible.has(row.id));
    if (selected.length > 5000) {
      this.publish({
        ...this.#state,
        failure: { code: "invalid_payroll_period", fields: {}, parameters: {} },
      });
      return;
    }
    this.publish({
      ...this.#state,
      selected: Object.freeze(selected),
      selectedPage: Math.min(
        this.#state.selectedPage,
        Math.max(0, Math.ceil(selected.length / 50) - 1),
      ),
      failure: null,
    });
  };
  remove = (id: string): void => {
    if (!this.#active || this.#state.stage !== "editing") return;
    const selected = this.#state.selected.filter((row) => row.id !== id);
    this.publish({
      ...this.#state,
      selected: Object.freeze(selected),
      selectedPage: Math.min(
        this.#state.selectedPage,
        Math.max(0, Math.ceil(selected.length / 50) - 1),
      ),
      failure: null,
    });
  };
  selectPage = (index: number): void => {
    if (
      !this.#active ||
      !Number.isSafeInteger(index) ||
      index < 0 ||
      index > Math.max(0, Math.ceil(this.#state.selected.length / 50) - 1)
    )
      return;
    this.publish({ ...this.#state, selectedPage: index });
  };
  review = (fields: PayrollPeriodFields): void => {
    if (!this.#active || this.#state.stage !== "editing") return;
    const result = this.actions.reviewPeriodCreation.execute(this.access, {
      ...fields,
      id: this.nextIdentifier(),
      employeeIds: this.#state.selected.map((row) => row.id),
    });
    if (!result.ok) {
      this.publish({ ...this.#state, failure: result.failure });
      return;
    }
    this.#search?.abort();
    this.#search = null;
    this.publish({
      ...this.#state,
      stage: "reviewing",
      review: result.value,
      page: null,
      loading: false,
      selectedPage: 0,
      failure: null,
      searchFailure: null,
    });
  };
  edit = (): void => {
    if (!this.#active || this.#state.stage !== "reviewing") return;
    this.publish({ ...this.#state, stage: "editing", review: null });
    void this.search(this.#state.query, this.#state.after);
  };
  confirm = async (): Promise<void> => {
    if (!this.#active || this.#state.stage !== "reviewing" || !this.#state.review) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      input: this.#state.review,
    });
    await this.send(false);
  };
  retry = async (): Promise<void> => {
    if (this.#active && this.#state.stage === "unconfirmed") await this.send(true);
  };
  private async send(wasUnconfirmed: boolean): Promise<void> {
    const submission = this.#submission;
    if (!submission) return;
    const pending = new AbortController();
    this.#save = pending;
    this.publish({
      ...this.#state,
      stage: "saving",
      failure: null,
      operationId: submission.operation,
    });
    try {
      let result: Result<MutationReceipt>;
      try {
        result = await this.actions.createPeriod.execute(
          this.access,
          submission.operation,
          submission.input,
          pending.signal,
        );
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#save !== pending) return;
      if (result.ok) {
        this.#submission = null;
        this.publish({
          ...initialPayrollPeriodCreationState,
          stage: "saved",
          receipt: result.value,
        });
        return;
      }
      const lost = payrollPeriodAccessLost(result.failure);
      if (!wasUnconfirmed && payrollPeriodCommandRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          stage: lost ? "unavailable" : "reviewing",
          selected: lost ? [] : this.#state.selected,
          review: lost ? null : this.#state.review,
          failure: result.failure,
          operationId: null,
        });
      } else
        this.publish({
          ...this.#state,
          stage: "unconfirmed",
          selected: lost ? [] : this.#state.selected,
          review: lost ? null : this.#state.review,
          failure: result.failure,
        });
    } finally {
      if (this.#save === pending) this.#save = null;
    }
  }
  private publish(state: PayrollPeriodCreationState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
