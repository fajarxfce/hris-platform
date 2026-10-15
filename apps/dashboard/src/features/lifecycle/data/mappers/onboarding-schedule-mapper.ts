import { Temporal } from "@js-temporal/polyfill";
import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { JobId } from "../../../jobs/domain/entities/background-job";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { LifecycleCaseId } from "../../domain/entities/lifecycle-case";
import type {
  OnboardingSchedule,
  OnboardingScheduleId,
} from "../../domain/entities/onboarding-schedule";
import type { OnboardingScheduleReview } from "../../domain/entities/onboarding-schedule-review";
import type { OnboardingScheduleSelection } from "../../domain/entities/onboarding-schedule-selection";
import type {
  OnboardingScheduleDto,
  OnboardingScheduleReceiptDto,
  OnboardingScheduleReviewDto,
  OnboardingSchedulesDto,
} from "../models/onboarding-schedule-dto";
import { toLifecycleEmployee } from "./lifecycle-case-mapper";
import { toLifecycleTemplate } from "./lifecycle-template-mapper";

/** Validate the server's retained boundary; this never selects dates or reads the clock. */
function onboardingWindowMatches(
  target: string,
  begins: string,
  zone: string,
  from: string,
  until: string,
): boolean {
  if (![target, begins].every(isCalendarDate) || begins > target) return false;
  try {
    const start =
      Temporal.PlainDate.from(begins)
        .toPlainDateTime("00:00")
        .toZonedDateTime(zone, { disambiguation: "later" }).epochNanoseconds / 1000n;
    const followingDay =
      Temporal.PlainDate.from(target)
        .add({ days: 1 })
        .toPlainDateTime("00:00")
        .toZonedDateTime(zone, { disambiguation: "later" }).epochNanoseconds / 1000n;
    const limit = start + 604_800_000_000n;
    const expectedEnd = followingDay < limit ? followingDay : limit;
    return (
      utcInstantMicroseconds(from) === start &&
      utcInstantMicroseconds(until) === expectedEnd &&
      expectedEnd > start
    );
  } catch {
    return false;
  }
}
export function toOnboardingSchedule(
  dto: OnboardingScheduleDto,
  company: CompanyId,
  employee: EmployeeId,
): OnboardingSchedule {
  const template = toLifecycleTemplate(dto.template, company);
  const created = utcInstantMicroseconds(dto.createdAt),
    start = utcInstantMicroseconds(dto.scheduledFor),
    finished = dto.finishedAt === null ? null : utcInstantMicroseconds(dto.finishedAt);
  const pending = dto.status === "SCHEDULED",
    started = dto.status === "STARTED",
    terminalJob = !["QUEUED", "RUNNING"].includes(dto.job.status);
  if (
    dto.employmentId.toLowerCase() !== employee ||
    template.kind !== "ONBOARDING" ||
    !template.active ||
    dto.appliedEmploymentRevision > dto.employmentVersion ||
    !dto.reason.trim() ||
    created === null ||
    start === null ||
    created >= start ||
    !onboardingWindowMatches(
      dto.targetDate,
      dto.startsOn,
      dto.companyTimezone,
      dto.scheduledFor,
      dto.validUntil,
    ) ||
    Object.keys(dto.assignees).some((key) => !template.tasks.some((task) => task.key === key)) ||
    (dto.finishedAt !== null && (finished === null || finished < created)) ||
    (dto.job.finishedAt !== null && utcInstantMicroseconds(dto.job.finishedAt) === null) ||
    terminalJob !== (dto.job.finishedAt !== null) ||
    started !== (dto.job.status === "SUCCEEDED") ||
    dto.job.completedItems !== (started ? 1 : 0) ||
    (pending
      ? dto.version !== 0 || dto.finishedAt !== null || dto.failureCode !== null
      : dto.version !== 1 ||
        dto.finishedAt === null ||
        (started
          ? dto.failureCode !== null || dto.job.cancellationRequested
          : dto.failureCode === null ||
            dto.job.status !== dto.status ||
            dto.job.failureCode !== dto.failureCode)) ||
    dto.actions.some(
      (action) =>
        !pending ||
        (action === "cancel"
          ? terminalJob || dto.job.cancellationRequested
          : !terminalJob || dto.job.status === "SUCCEEDED"),
    )
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...dto,
    companyId: company,
    id: dto.id.toLowerCase() as OnboardingScheduleId,
    caseId: dto.caseId.toLowerCase() as LifecycleCaseId,
    employmentId: employee,
    createdBy: dto.createdBy.toLowerCase() as AccountId,
    template,
    assignees: Object.freeze(
      Object.fromEntries(
        Object.entries(dto.assignees).map(([key, id]) => [key, id.toLowerCase() as AccountId]),
      ),
    ),
    job: Object.freeze({ ...dto.job, id: dto.job.id.toLowerCase() as JobId }),
    actions: Object.freeze([...dto.actions]),
  });
}
export function toOnboardingSchedules(
  dto: OnboardingSchedulesDto,
  company: CompanyId,
  employee: EmployeeId,
): readonly OnboardingSchedule[] {
  const items = dto.items.map((item) => toOnboardingSchedule(item, company, employee));
  if (
    items.some((item, index) => item.attempt !== index + 1) ||
    new Set(items.map((i) => i.id)).size !== items.length ||
    new Set(items.map((i) => i.caseId)).size !== items.length ||
    new Set(items.map((i) => i.job.id)).size !== items.length ||
    items.filter((i) => i.status === "SCHEDULED").length > 1
  )
    throw new InvalidHttpResponseError();
  return Object.freeze(items);
}
export function toOnboardingScheduleReview(
  dto: OnboardingScheduleReviewDto,
  company: CompanyId,
  employeeId: EmployeeId,
  selection: OnboardingScheduleSelection,
): OnboardingScheduleReview {
  const employee = toLifecycleEmployee(dto.employee, employeeId),
    template = toLifecycleTemplate(dto.template, company);
  if (
    template.id !== selection.templateId ||
    dto.targetDate !== selection.targetDate ||
    dto.startsOn !== selection.startsOn ||
    !isCalendarDate(dto.today) ||
    dto.appliedEmploymentRevision > dto.employmentVersion ||
    new Set(dto.blockers).size !== dto.blockers.length ||
    !onboardingWindowMatches(
      dto.targetDate,
      dto.startsOn,
      dto.companyTimezone,
      dto.scheduledFor,
      dto.validUntil,
    )
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({
    ...dto,
    companyId: company,
    employee,
    template,
    blockers: Object.freeze([...dto.blockers]),
  });
}
export function toOnboardingScheduleReceipt(
  dto: OnboardingScheduleReceiptDto,
  id: string,
  version: number,
) {
  if (dto.id.toLowerCase() !== id || dto.version !== version) throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
