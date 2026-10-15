import type { AccountId } from "../../../../core/domain/identifiers";
import type { JobId } from "../../../jobs/domain/entities/background-job";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { LifecycleCaseId } from "./lifecycle-case";
import type { OnboardingScheduleId } from "./onboarding-schedule";
import type { OnboardingScheduleSelection } from "./onboarding-schedule-selection";
export type OnboardingScheduleCreation = OnboardingScheduleSelection &
  Readonly<{
    id: OnboardingScheduleId;
    caseId: LifecycleCaseId;
    employmentId: EmployeeId;
    employmentVersion: number;
    templateVersion: number;
    basisRevision: string;
    assignees: Readonly<Record<string, AccountId>>;
    reason: string;
  }>;
export type OnboardingScheduleChange = Readonly<{
  id: OnboardingScheduleId;
  employmentId: EmployeeId;
  jobId: JobId;
  expectedVersion: number;
  expectedJobVersion: number;
  reason: string;
}>;
