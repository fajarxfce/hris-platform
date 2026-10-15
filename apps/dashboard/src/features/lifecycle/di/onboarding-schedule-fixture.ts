import type { AccountId, CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { JobId } from "../../jobs/domain/entities/background-job";
import type { Employee, EmployeeId } from "../../people/domain/entities/employee";
import {
  toOnboardingSchedule,
  toOnboardingScheduleReview,
} from "../data/mappers/onboarding-schedule-mapper";
import type {
  OnboardingScheduleDto,
  OnboardingScheduleReviewDto,
} from "../data/models/onboarding-schedule-dto";
import type { LifecycleCaseId } from "../domain/entities/lifecycle-case";
import type { LifecycleTemplateId } from "../domain/entities/lifecycle-template";
import type { OnboardingScheduleId } from "../domain/entities/onboarding-schedule";
export const onboardingCompany = "10000000-0000-4000-8000-000000000001" as CompanyId;
export const onboardingAccount = "20000000-0000-4000-8000-000000000001" as AccountId;
export const onboardingEmployee = "30000000-0000-4000-8000-000000000001" as EmployeeId;
export const onboardingId = "40000000-abcd-4000-8000-000000000001" as OnboardingScheduleId;
export const onboardingCaseId = "50000000-0000-4000-8000-000000000001" as LifecycleCaseId;
export const onboardingJobId = "60000000-0000-4000-8000-000000000001" as JobId;
export const onboardingOperation = "70000000-0000-4000-8000-000000000001" as OperationId;
export const onboardingTemplateId = "80000000-0000-4000-8000-000000000001" as LifecycleTemplateId;
export const onboardingPermissions = [
  "people.read",
  "people.lifecycle.read",
  "people.lifecycle.manage",
];
export const onboardingAccess = {
  companyId: onboardingCompany,
  permissions: onboardingPermissions,
};
export const onboardingWire: OnboardingScheduleDto = {
  id: onboardingId,
  caseId: onboardingCaseId,
  employmentId: onboardingEmployee,
  status: "SCHEDULED",
  version: 0,
  employmentVersion: 2,
  appliedEmploymentRevision: 1,
  template: {
    id: onboardingTemplateId,
    code: "JOINING",
    name: "New employee",
    kind: "ONBOARDING",
    active: true,
    version: 3,
    tasks: [
      { key: "equipment", title: "Prepare equipment", required: true, dueDays: -1 },
      { key: "welcome", title: "Team introduction", required: false, dueDays: 0 },
    ],
  },
  assignees: { equipment: onboardingAccount },
  targetDate: "2026-10-02",
  startsOn: "2026-10-01",
  companyTimezone: "Asia/Jakarta",
  scheduledFor: "2026-09-30T17:00:00Z",
  validUntil: "2026-10-02T17:00:00Z",
  attempt: 1,
  createdBy: onboardingAccount,
  createdAt: "2026-09-29T01:00:00Z",
  reason: "Prepare joining checklist",
  finishedAt: null,
  failureCode: null,
  job: {
    id: onboardingJobId,
    status: "QUEUED",
    version: 0,
    attempts: 0,
    cancellationRequested: false,
    completedItems: 0,
    failureCode: null,
    finishedAt: null,
  },
  actions: ["cancel"],
};
export const onboardingSelection = {
  templateId: onboardingTemplateId,
  targetDate: onboardingWire.targetDate,
  startsOn: onboardingWire.startsOn,
};
export const onboardingReviewWire: OnboardingScheduleReviewDto = {
  employee: { id: onboardingEmployee, employeeNumber: "JOIN-001", name: "Joining Employee" },
  employmentVersion: 2,
  appliedEmploymentRevision: 1,
  template: onboardingWire.template,
  targetDate: onboardingWire.targetDate,
  startsOn: onboardingWire.startsOn,
  companyTimezone: onboardingWire.companyTimezone,
  today: "2026-09-29",
  scheduledFor: onboardingWire.scheduledFor,
  validUntil: onboardingWire.validUntil,
  basisRevision: "a".repeat(64),
  attempts: 0,
  blockers: [],
};
export const onboardingDetails = toOnboardingSchedule(
  onboardingWire,
  onboardingCompany,
  onboardingEmployee,
);
export const onboardingReview = toOnboardingScheduleReview(
  onboardingReviewWire,
  onboardingCompany,
  onboardingEmployee,
  onboardingSelection,
);
export const onboardingEmployeeDetails: Employee = {
  id: onboardingEmployee,
  companyId: onboardingCompany,
  employeeNumber: "JOIN-001",
  legalName: "Joining Employee",
  email: null,
  version: 2,
  appliedRevision: 1,
  terms: {
    effectiveFrom: "2026-10-02",
    startDate: "2026-10-02",
    endDate: null,
    contract: "PERMANENT",
    status: "ACTIVE",
  },
};
export function stoppedOnboarding(): OnboardingScheduleDto {
  return {
    ...onboardingWire,
    job: {
      ...onboardingWire.job,
      status: "FAILED",
      version: 4,
      attempts: 4,
      failureCode: "job_attempts_exhausted",
      finishedAt: "2026-10-01T00:00:00Z",
    },
    actions: ["recover"],
  };
}
export function deferred<T>() {
  let resolve!: (value: T) => void, reject!: (value: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
