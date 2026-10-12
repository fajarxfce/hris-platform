import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  WorkPeriodActionContext,
  WorkPeriodIntent,
} from "../../domain/entities/work-period-action-context";
import {
  reviewWorkPeriodAction,
  workPeriodCommandRejected,
  workPeriodReadDenied,
  workPeriodReviewChanged,
} from "../../domain/policies/work-period-action-policy";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import {
  initialWorkPeriodActionState,
  type WorkPeriodActionState,
} from "../models/work-period-action-state";

type Submission = Readonly<{
  operation: OperationId;
  context: WorkPeriodActionContext;
  reason: string;
}>;

export class WorkPeriodActionController {
  #state = initialWorkPeriodActionState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();

  constructor(
    private readonly actions: Pick<
      WorkforceUseCases,
      "loadWorkPeriodOverview" | "closeWorkPeriod" | "recoverWorkPeriod"
    >,
    private readonly access: CompanyAccess,
    private readonly month: string,
    readonly intent: WorkPeriodIntent,
    private readonly nextIdentifier: () => string,
  ) {}

  getSnapshot = (): WorkPeriodActionState => this.#state;
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
    this.publish(initialWorkPeriodActionState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialWorkPeriodActionState);
    try {
      const result = this.access.permissions.includes("workforce.close")
        ? await this.actions.loadWorkPeriodOverview.execute(this.access, this.month, pending.signal)
        : failed("access_denied");
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (!result.ok) {
        this.publish({
          ...initialWorkPeriodActionState,
          stage: "unavailable",
          failure: result.failure,
        });
        return;
      }
      const context = reviewWorkPeriodAction(result.value, this.intent);
      this.publish(
        context.ok
          ? {
              ...initialWorkPeriodActionState,
              stage: "reviewing",
              overview: result.value,
              context: context.value,
            }
          : { ...initialWorkPeriodActionState, stage: "unavailable", failure: context.failure },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialWorkPeriodActionState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  confirm = async (reason: string): Promise<void> => {
    if (!this.#active || this.#state.stage !== "reviewing" || !this.#state.context) return;
    this.#submission = Object.freeze({
      operation: this.nextIdentifier() as OperationId,
      context: Object.freeze({ ...this.#state.context }),
      reason,
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
        const action =
          this.intent === "close" ? this.actions.closeWorkPeriod : this.actions.recoverWorkPeriod;
        result = await action.execute(
          this.access,
          submission.operation,
          submission.context,
          submission.reason,
          pending.signal,
        );
      } catch {
        if (pending.signal.aborted) return;
        result = failed("unexpected_error");
      }
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) {
        this.#submission = null;
        this.publish({ ...initialWorkPeriodActionState, stage: "saved", receipt: result.value });
        return;
      }
      const denied = workPeriodReadDenied(result.failure);
      const overview = denied ? null : this.#state.overview;
      const context = denied ? null : this.#state.context;
      if (!wasUnconfirmed && workPeriodCommandRejected(result.failure)) {
        this.#submission = null;
        this.publish({
          ...this.#state,
          overview,
          context,
          failure: result.failure,
          operationId: null,
          stage: !context
            ? "unavailable"
            : workPeriodReviewChanged(result.failure)
              ? "conflict"
              : "reviewing",
        });
      } else {
        this.publish({
          ...this.#state,
          overview,
          context,
          stage: "unconfirmed",
          failure: result.failure,
        });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: WorkPeriodActionState): void {
    this.#state = state;
    for (const listener of this.#listeners) listener();
  }
}
