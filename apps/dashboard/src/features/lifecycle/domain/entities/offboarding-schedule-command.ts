import type { JobId } from "../../../jobs/domain/entities/background-job";
import type { LifecycleCaseId } from "./lifecycle-case";
import type { OffboardingScheduleId } from "./offboarding-schedule";
export type OffboardingScheduleCreation = Readonly<{
  id: OffboardingScheduleId;
  caseId: LifecycleCaseId;
  expectedVersion: number;
  employmentVersion: number;
  basisRevision: string;
  reason: string;
}>;
export type OffboardingScheduleChange = Readonly<{
  id: OffboardingScheduleId;
  caseId: LifecycleCaseId;
  jobId: JobId;
  expectedVersion: number;
  expectedJobVersion: number;
  reason: string;
}>;
