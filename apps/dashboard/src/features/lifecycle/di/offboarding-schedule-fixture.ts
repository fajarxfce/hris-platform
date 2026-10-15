import type { AccountId, CompanyId, OperationId } from "../../../core/domain/identifiers";
import type { JobId } from "../../jobs/domain/entities/background-job";
import type { EmployeeId } from "../../people/domain/entities/employee";
import {
  toOffboardingSchedule,
  toOffboardingScheduleReview,
} from "../data/mappers/offboarding-schedule-mapper";
import type { LifecycleCaseDto } from "../data/models/lifecycle-case-dto";
import type {
  OffboardingScheduleDto,
  OffboardingScheduleReviewDto,
} from "../data/models/offboarding-schedule-dto";
import type { LifecycleCaseId } from "../domain/entities/lifecycle-case";
import type { OffboardingScheduleId } from "../domain/entities/offboarding-schedule";
export const scheduleCompany = "10000000-0000-4000-8000-000000000001" as CompanyId;
export const scheduleAccount = "20000000-0000-4000-8000-000000000001" as AccountId;
export const scheduleOperation = "30000000-0000-4000-8000-000000000001" as OperationId;
export const scheduleCaseId = "a0000000-abcd-4000-8000-000000000001" as LifecycleCaseId;
export const scheduleId = "b0000000-abcd-4000-8000-000000000001" as OffboardingScheduleId;
export const scheduleJobId = "c0000000-abcd-4000-8000-000000000001" as JobId;
export const schedulePermissions = [
  "people.lifecycle.read",
  "people.lifecycle.manage",
  "people.manage",
  "people.offboard",
];
export const scheduleAccess = { companyId: scheduleCompany, permissions: schedulePermissions };
export const scheduleCase: LifecycleCaseDto = {
  id: scheduleCaseId,
  employmentId: "40000000-0000-4000-8000-000000000001" as EmployeeId,
  employee: {
    id: "40000000-0000-4000-8000-000000000001",
    name: "Departure Employee",
    employeeNumber: "E-001",
  },
  kind: "OFFBOARDING",
  status: "OPEN",
  version: 3,
  targetDate: "2026-09-30",
  templateId: "90000000-0000-4000-8000-000000000001",
  templateVersion: 0,
  templateName: "Departure",
  createdBy: scheduleAccount,
  createdAt: "2026-09-29T00:00:00Z",
  tasks: [
    {
      key: "equipment",
      title: "Equipment",
      required: true,
      status: "DONE",
      dueDate: "2026-09-30",
      assigneeId: null,
      completedBy: scheduleAccount,
      completedAt: "2026-09-30T01:00:00Z",
    },
  ],
};
export const scheduleWire: OffboardingScheduleDto = {
  id: scheduleId,
  caseId: scheduleCaseId,
  employmentId: scheduleCase.employmentId,
  status: "SCHEDULED",
  version: 0,
  caseVersion: 3,
  employmentVersion: 7,
  appliedEmploymentRevision: 7,
  lastWorkingDate: "2026-09-30",
  companyTimezone: "Asia/Jakarta",
  scheduledFor: "2026-09-30T17:00:00Z",
  validUntil: "2026-10-07T17:00:00Z",
  attempt: 1,
  createdBy: scheduleAccount,
  createdAt: "2026-09-30T02:00:00Z",
  reason: "Departure authorized",
  finishedAt: null,
  failureCode: null,
  job: {
    id: scheduleJobId,
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
export const scheduleReviewWire: OffboardingScheduleReviewDto = {
  case: scheduleCase,
  employmentVersion: 7,
  appliedEmploymentRevision: 7,
  companyTimezone: scheduleWire.companyTimezone,
  today: "2026-09-30",
  scheduledFor: scheduleWire.scheduledFor,
  validUntil: scheduleWire.validUntil,
  basisRevision: "a".repeat(64),
  attempts: 0,
  blockers: [],
};
export const scheduleDetails = toOffboardingSchedule(scheduleWire, scheduleCompany, scheduleCaseId);
export const scheduleReview = toOffboardingScheduleReview(
  scheduleReviewWire,
  scheduleCompany,
  scheduleCaseId,
);
export function stoppedSchedule(): OffboardingScheduleDto {
  return {
    ...scheduleWire,
    job: {
      ...scheduleWire.job,
      status: "FAILED",
      version: 4,
      attempts: 4,
      failureCode: "job_attempts_exhausted",
      finishedAt: "2026-10-01T01:00:00Z",
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
