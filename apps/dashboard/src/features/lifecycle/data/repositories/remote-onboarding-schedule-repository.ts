import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { OnboardingScheduleId } from "../../domain/entities/onboarding-schedule";
import type {
  OnboardingScheduleChange,
  OnboardingScheduleCreation,
} from "../../domain/entities/onboarding-schedule-command";
import type { OnboardingScheduleSelection } from "../../domain/entities/onboarding-schedule-selection";
import type { OnboardingScheduleRepository } from "../../domain/repositories/onboarding-schedule-repository";
import type { OnboardingScheduleDataSource } from "../datasources/onboarding-schedule-data-source";
import {
  toOnboardingSchedule,
  toOnboardingScheduleReceipt,
  toOnboardingScheduleReview,
  toOnboardingSchedules,
} from "../mappers/onboarding-schedule-mapper";
export class RemoteOnboardingScheduleRepository implements OnboardingScheduleRepository {
  constructor(private readonly source: OnboardingScheduleDataSource) {}
  review(
    company: CompanyId,
    employee: EmployeeId,
    selection: OnboardingScheduleSelection,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toOnboardingScheduleReview(
        await this.source.review(company, employee, selection, signal),
        company,
        employee,
        selection,
      ),
    );
  }
  list(company: CompanyId, employee: EmployeeId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toOnboardingSchedules(await this.source.list(company, employee, signal), company, employee),
    );
  }
  get(company: CompanyId, employee: EmployeeId, id: OnboardingScheduleId, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const details = toOnboardingSchedule(
        await this.source.get(company, employee, id, signal),
        company,
        employee,
      );
      if (details.id !== id) throw new InvalidHttpResponseError();
      return details;
    });
  }
  schedule(
    company: CompanyId,
    operation: OperationId,
    input: OnboardingScheduleCreation,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toOnboardingScheduleReceipt(
        await this.source.schedule(
          company,
          input.employmentId,
          operation,
          {
            id: input.id,
            caseId: input.caseId,
            templateId: input.templateId,
            targetDate: input.targetDate,
            startsOn: input.startsOn,
            employmentVersion: input.employmentVersion,
            templateVersion: input.templateVersion,
            basisRevision: input.basisRevision,
            assignees: input.assignees,
            reason: input.reason,
          },
          signal,
        ),
        input.id,
        0,
      ),
    );
  }
  cancel(
    company: CompanyId,
    operation: OperationId,
    input: OnboardingScheduleChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toOnboardingScheduleReceipt(
        await this.source.cancel(
          company,
          input.employmentId,
          input.id,
          operation,
          {
            expectedVersion: input.expectedVersion,
            expectedJobVersion: input.expectedJobVersion,
            reason: input.reason,
          },
          signal,
        ),
        input.jobId,
        input.expectedJobVersion + 1,
      ),
    );
  }
  recover(
    company: CompanyId,
    operation: OperationId,
    input: OnboardingScheduleChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toOnboardingScheduleReceipt(
        await this.source.recover(
          company,
          input.employmentId,
          input.id,
          operation,
          {
            expectedVersion: input.expectedVersion,
            expectedJobVersion: input.expectedJobVersion,
            reason: input.reason,
          },
          signal,
        ),
        input.id,
        input.expectedVersion + 1,
      ),
    );
  }
}
