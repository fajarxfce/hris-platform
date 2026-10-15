import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type {
  OffboardingSchedule,
  OffboardingScheduleAction,
} from "../../domain/entities/offboarding-schedule";
import type { OffboardingScheduleChange } from "../../domain/entities/offboarding-schedule-command";
import {
  canChangeOffboardingSchedule,
  offboardingScheduleAccessLost,
  offboardingScheduleChanged,
  offboardingScheduleCommandRejected,
} from "../../domain/policies/offboarding-schedule-policy";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import {
  initialOffboardingScheduleCommand,
  type OffboardingScheduleCommandState,
} from "../models/offboarding-schedule-state";

type Submission = Readonly<{ operation: OperationId; input: OffboardingScheduleChange }>;
export class OffboardingScheduleChangeController {
  #state = initialOffboardingScheduleCommand<OffboardingSchedule>();
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly load: LifecycleUseCases["loadOffboardingSchedule"],
    private readonly command: LifecycleUseCases["cancelOffboardingSchedule"],
    private readonly action: OffboardingScheduleAction,
    private readonly access: CompanyAccess,
    private readonly caseId: string,
    private readonly id: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): OffboardingScheduleCommandState<OffboardingSchedule> => this.#state;
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
    this.publish(initialOffboardingScheduleCommand());
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialOffboardingScheduleCommand());
    try {
      const result = await this.load.execute(this.access, this.caseId, this.id, pending.signal);
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...this.#state, stage: "reviewing", review: result.value }
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
  save = async (reason: string): Promise<void> => {
    const review = this.#state.review;
    if (
      !this.#active ||
      this.#state.stage !== "reviewing" ||
      !review ||
      !canChangeOffboardingSchedule(this.access, review, this.action)
    )
      return;
    try {
      this.#submission = Object.freeze({
        operation: this.nextIdentifier() as OperationId,
        input: Object.freeze({
          id: review.id,
          caseId: review.caseId,
          jobId: review.job.id,
          expectedVersion: review.version,
          expectedJobVersion: review.job.version,
          reason: reason.trim(),
        }),
      });
    } catch {
      this.publish({
        ...this.#state,
        failure: { code: "unexpected_error", fields: {}, parameters: {} },
      });
      return;
    }
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
        result = await this.command.execute(
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
          ...initialOffboardingScheduleCommand<OffboardingSchedule>(),
          stage: "saved",
          receipt: result.value,
          scheduleId: submission.input.id,
        });
      } else {
        const review =
          offboardingScheduleAccessLost(result.failure) ||
          ["lifecycle_case_not_found", "offboarding_schedule_not_found"].includes(
            result.failure.code,
          )
            ? null
            : this.#state.review;
        if (!wasUnconfirmed && offboardingScheduleCommandRejected(result.failure)) {
          this.#submission = null;
          this.publish({
            ...this.#state,
            review,
            failure: result.failure,
            operationId: null,
            stage:
              review === null
                ? "unavailable"
                : offboardingScheduleChanged(result.failure)
                  ? "conflict"
                  : "reviewing",
          });
        } else
          this.publish({ ...this.#state, review, stage: "unconfirmed", failure: result.failure });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: OffboardingScheduleCommandState<OffboardingSchedule>): void {
    this.#state = state;
    for (const listener of [...this.#listeners]) listener();
  }
}
