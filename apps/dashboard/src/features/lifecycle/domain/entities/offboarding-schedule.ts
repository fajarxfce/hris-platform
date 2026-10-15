import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import type { JobId, JobStatus } from "../../../jobs/domain/entities/background-job";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { LifecycleCaseId } from "./lifecycle-case";

export type OffboardingScheduleId = string & { readonly offboardingScheduleId: unique symbol };
export type OffboardingScheduleAction = "cancel" | "recover";
export type OffboardingSchedule = Readonly<{
  id: OffboardingScheduleId;
  companyId: CompanyId;
  caseId: LifecycleCaseId;
  employmentId: EmployeeId;
  status: "SCHEDULED" | "COMPLETED" | "FAILED" | "CANCELLED";
  version: number;
  caseVersion: number;
  employmentVersion: number;
  appliedEmploymentRevision: number;
  lastWorkingDate: string;
  companyTimezone: string;
  scheduledFor: string;
  validUntil: string;
  attempt: number;
  createdBy: AccountId;
  createdAt: string;
  reason: string;
  finishedAt: string | null;
  failureCode: string | null;
  job: Readonly<{
    id: JobId;
    status: JobStatus;
    version: number;
    attempts: number;
    cancellationRequested: boolean;
    completedItems: number;
    failureCode: string | null;
    finishedAt: string | null;
  }>;
  actions: readonly OffboardingScheduleAction[];
}>;
