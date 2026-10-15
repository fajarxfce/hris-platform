import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result } from "../../../../core/domain/result";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LoadEmployee } from "../../../people/domain/usecases/load-employee";
import type { LifecycleAssignee } from "../../domain/entities/lifecycle-assignee";
import type { LifecycleCaseId } from "../../domain/entities/lifecycle-case";
import type {
  LifecycleTemplate,
  LifecycleTemplateId,
} from "../../domain/entities/lifecycle-template";
import type { OnboardingScheduleId } from "../../domain/entities/onboarding-schedule";
import type { OnboardingScheduleCreation } from "../../domain/entities/onboarding-schedule-command";
import {
  canReadOnboardingSchedules,
  canScheduleOnboarding,
  onboardingScheduleAccessLost,
  onboardingScheduleChanged,
  onboardingScheduleCommandRejected,
} from "../../domain/policies/onboarding-schedule-policy";
import type { LifecycleUseCases } from "../contracts/lifecycle-use-cases";
import {
  initialOnboardingScheduleStartState,
  type OnboardingScheduleStartState,
} from "../models/onboarding-schedule-state";
export type OnboardingScheduleFields = Readonly<{
  reason: string;
  assignees: Readonly<Record<string, LifecycleAssignee>>;
}>;
type Submission = Readonly<{ operation: OperationId; input: OnboardingScheduleCreation }>;
/** Owns employee acquisition, the selected review and exactly one uncertain command. */
export class OnboardingScheduleStartController {
  #state = initialOnboardingScheduleStartState;
  #active = false;
  #pending: AbortController | null = null;
  #submission: Submission | null = null;
  #listeners = new Set<() => void>();
  constructor(
    private readonly loadEmployee: Pick<LoadEmployee, "execute">,
    private readonly actions: Pick<
      LifecycleUseCases,
      "loadOnboardingScheduleReview" | "scheduleOnboarding"
    >,
    private readonly access: CompanyAccess,
    private readonly employeeId: string,
    private readonly asOf: string,
    private readonly nextIdentifier: () => string,
  ) {}
  getSnapshot = (): OnboardingScheduleStartState => this.#state;
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
    this.publish(initialOnboardingScheduleStartState);
  };
  refresh = async (): Promise<void> => {
    if (!this.#active || this.#submission) return;
    this.#pending?.abort();
    const pending = new AbortController();
    this.#pending = pending;
    this.publish(initialOnboardingScheduleStartState);
    try {
      if (!canReadOnboardingSchedules(this.access.permissions)) {
        this.publish({
          ...this.#state,
          stage: "unavailable",
          failure: { code: "onboarding_access_required", fields: {}, parameters: {} },
        });
        return;
      }
      const result = await this.loadEmployee.execute(
        this.access,
        this.employeeId,
        this.asOf,
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      this.publish(
        result.ok
          ? { ...initialOnboardingScheduleStartState, stage: "editing", employee: result.value }
          : {
              ...initialOnboardingScheduleStartState,
              stage: "unavailable",
              failure: result.failure,
            },
      );
    } catch {
      if (!pending.signal.aborted && this.#pending === pending)
        this.publish({
          ...initialOnboardingScheduleStartState,
          stage: "unavailable",
          failure: { code: "unexpected_error", fields: {}, parameters: {} },
        });
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  };
  prepare = async (template: LifecycleTemplate | null, startsOn: string): Promise<void> => {
    const employee = this.#state.employee;
    if (!this.#active || this.#state.stage !== "editing" || !employee || this.#submission) return;
    const pending = new AbortController();
    this.#pending = pending;
    this.publish({ ...this.#state, stage: "preparing", review: null, failure: null });
    try {
      const result = await this.actions.loadOnboardingScheduleReview.execute(
        this.access,
        employee.id,
        {
          templateId: template?.id ?? ("" as LifecycleTemplateId),
          targetDate: employee.terms.startDate,
          startsOn,
        },
        pending.signal,
      );
      if (pending.signal.aborted || this.#pending !== pending) return;
      if (result.ok) this.publish({ ...this.#state, stage: "reviewing", review: result.value });
      else {
        const denied =
          onboardingScheduleAccessLost(result.failure) ||
          result.failure.code === "employee_not_found";
        this.publish({
          ...this.#state,
          stage: denied ? "unavailable" : "editing",
          employee: denied ? null : employee,
          review: null,
          failure: result.failure,
        });
      }
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
  revise = (): void => {
    if (this.#active && !this.#submission && ["reviewing", "conflict"].includes(this.#state.stage))
      this.publish({ ...this.#state, stage: "editing", review: null, failure: null });
  };
  save = async (fields: OnboardingScheduleFields): Promise<void> => {
    const review = this.#state.review;
    if (
      !this.#active ||
      this.#state.stage !== "reviewing" ||
      !review ||
      !canScheduleOnboarding(this.access, review)
    )
      return;
    try {
      this.#submission = Object.freeze({
        operation: this.nextIdentifier() as OperationId,
        input: Object.freeze({
          id: this.nextIdentifier() as OnboardingScheduleId,
          caseId: this.nextIdentifier() as LifecycleCaseId,
          employmentId: review.employee.id,
          employmentVersion: review.employmentVersion,
          templateId: review.template.id,
          templateVersion: review.template.version,
          targetDate: review.targetDate,
          startsOn: review.startsOn,
          basisRevision: review.basisRevision,
          assignees: Object.freeze(
            Object.fromEntries(
              Object.entries(fields.assignees).map(([key, member]) => [key, member.id]),
            ),
          ),
          reason: fields.reason.trim(),
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
        result = await this.actions.scheduleOnboarding.execute(
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
          ...initialOnboardingScheduleStartState,
          stage: "saved",
          receipt: result.value,
          scheduleId: submission.input.id,
        });
      } else {
        const denied =
          onboardingScheduleAccessLost(result.failure) ||
          ["employee_not_found", "onboarding_schedule_not_found"].includes(result.failure.code);
        const review = denied ? null : this.#state.review,
          employee = denied ? null : this.#state.employee;
        if (!wasUnconfirmed && onboardingScheduleCommandRejected(result.failure)) {
          this.#submission = null;
          this.publish({
            ...this.#state,
            employee,
            review,
            failure: result.failure,
            operationId: null,
            stage:
              review === null
                ? "unavailable"
                : onboardingScheduleChanged(result.failure)
                  ? "conflict"
                  : "reviewing",
          });
        } else
          this.publish({
            ...this.#state,
            employee,
            review,
            stage: "unconfirmed",
            failure: result.failure,
          });
      }
    } finally {
      if (this.#pending === pending) this.#pending = null;
    }
  }
  private publish(state: OnboardingScheduleStartState): void {
    this.#state = state;
    for (const listener of [...this.#listeners]) listener();
  }
}
