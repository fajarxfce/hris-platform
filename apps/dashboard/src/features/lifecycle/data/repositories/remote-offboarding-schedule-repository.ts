import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { safeHttpCall } from "../../../../core/data/http/safe-http-call";
import type { CompanyId, OperationId } from "../../../../core/domain/identifiers";
import type { LifecycleCaseId } from "../../domain/entities/lifecycle-case";
import type { OffboardingScheduleId } from "../../domain/entities/offboarding-schedule";
import type {
  OffboardingScheduleChange,
  OffboardingScheduleCreation,
} from "../../domain/entities/offboarding-schedule-command";
import type { OffboardingScheduleRepository } from "../../domain/repositories/offboarding-schedule-repository";
import type { OffboardingScheduleDataSource } from "../datasources/offboarding-schedule-data-source";
import {
  toOffboardingSchedule,
  toOffboardingScheduleReceipt,
  toOffboardingScheduleReview,
  toOffboardingSchedules,
} from "../mappers/offboarding-schedule-mapper";
export class RemoteOffboardingScheduleRepository implements OffboardingScheduleRepository {
  constructor(private readonly source: OffboardingScheduleDataSource) {}
  review(company: CompanyId, caseId: LifecycleCaseId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toOffboardingScheduleReview(
        await this.source.review(company, caseId, signal),
        company,
        caseId,
      ),
    );
  }
  list(company: CompanyId, caseId: LifecycleCaseId, signal: AbortSignal) {
    return safeHttpCall(signal, async () =>
      toOffboardingSchedules(await this.source.list(company, caseId, signal), company, caseId),
    );
  }
  get(company: CompanyId, caseId: LifecycleCaseId, id: OffboardingScheduleId, signal: AbortSignal) {
    return safeHttpCall(signal, async () => {
      const detail = toOffboardingSchedule(
        await this.source.get(company, caseId, id, signal),
        company,
        caseId,
      );
      if (detail.id !== id) throw new InvalidHttpResponseError();
      return detail;
    });
  }
  schedule(
    company: CompanyId,
    operation: OperationId,
    input: OffboardingScheduleCreation,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toOffboardingScheduleReceipt(
        await this.source.schedule(
          company,
          input.caseId,
          operation,
          {
            id: input.id,
            expectedVersion: input.expectedVersion,
            employmentVersion: input.employmentVersion,
            basisRevision: input.basisRevision,
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
    input: OffboardingScheduleChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toOffboardingScheduleReceipt(
        await this.source.cancel(
          company,
          input.caseId,
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
    input: OffboardingScheduleChange,
    signal: AbortSignal,
  ) {
    return safeHttpCall(signal, async () =>
      toOffboardingScheduleReceipt(
        await this.source.recover(
          company,
          input.caseId,
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
