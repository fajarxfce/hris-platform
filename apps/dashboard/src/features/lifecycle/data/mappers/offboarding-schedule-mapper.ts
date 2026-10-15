import { Temporal } from "@js-temporal/polyfill";
import { InvalidHttpResponseError } from "../../../../core/data/http/http-response-error";
import { isCalendarDate } from "../../../../core/domain/calendar-date";
import type { AccountId, CompanyId } from "../../../../core/domain/identifiers";
import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";
import type { JobId } from "../../../jobs/domain/entities/background-job";
import type { EmployeeId } from "../../../people/domain/entities/employee";
import type { LifecycleCaseId } from "../../domain/entities/lifecycle-case";
import type {
  OffboardingSchedule,
  OffboardingScheduleId,
} from "../../domain/entities/offboarding-schedule";
import type { OffboardingScheduleReview } from "../../domain/entities/offboarding-schedule-review";
import type {
  OffboardingScheduleDto,
  OffboardingScheduleReceiptDto,
  OffboardingScheduleReviewDto,
  OffboardingSchedulesDto,
} from "../models/offboarding-schedule-dto";
import { toLifecycleCaseDetails } from "./lifecycle-case-mapper";

function scheduleWindowValid(
  lastDay: string,
  timezone: string,
  from: string,
  until: string,
): boolean {
  const start = utcInstantMicroseconds(from),
    end = utcInstantMicroseconds(until);
  if (
    !isCalendarDate(lastDay) ||
    start === null ||
    end === null ||
    end - start !== 604_800_000_000n
  )
    return false;
  try {
    // Match the server's later midnight when an offset repeats or skips local time.
    const boundary = Temporal.PlainDate.from(lastDay)
      .add({ days: 1 })
      .toPlainDateTime("00:00")
      .toZonedDateTime(timezone, { disambiguation: "later" });
    return boundary.epochNanoseconds / 1000n === start;
  } catch {
    return false;
  }
}
export function toOffboardingSchedule(
  dto: OffboardingScheduleDto,
  company: CompanyId,
  caseId: LifecycleCaseId,
): OffboardingSchedule {
  const created = utcInstantMicroseconds(dto.createdAt),
    start = utcInstantMicroseconds(dto.scheduledFor),
    finished = dto.finishedAt === null ? null : utcInstantMicroseconds(dto.finishedAt);
  const pending = dto.status === "SCHEDULED",
    completed = dto.status === "COMPLETED",
    terminalJob = !["QUEUED", "RUNNING"].includes(dto.job.status);
  if (
    dto.caseId.toLowerCase() !== caseId ||
    dto.appliedEmploymentRevision > dto.employmentVersion ||
    !dto.reason.trim() ||
    created === null ||
    start === null ||
    created >= start ||
    !scheduleWindowValid(
      dto.lastWorkingDate,
      dto.companyTimezone,
      dto.scheduledFor,
      dto.validUntil,
    ) ||
    (dto.finishedAt !== null && (finished === null || finished < created)) ||
    (dto.job.finishedAt !== null && utcInstantMicroseconds(dto.job.finishedAt) === null) ||
    terminalJob !== (dto.job.finishedAt !== null) ||
    completed !== (dto.job.status === "SUCCEEDED") ||
    dto.job.completedItems !== (completed ? 1 : 0) ||
    (pending
      ? dto.version !== 0 || dto.finishedAt !== null || dto.failureCode !== null
      : dto.version !== 1 ||
        dto.finishedAt === null ||
        (completed
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
    id: dto.id.toLowerCase() as OffboardingScheduleId,
    companyId: company,
    caseId,
    employmentId: dto.employmentId.toLowerCase() as EmployeeId,
    createdBy: dto.createdBy.toLowerCase() as AccountId,
    job: Object.freeze({ ...dto.job, id: dto.job.id.toLowerCase() as JobId }),
    actions: Object.freeze([...dto.actions]),
  });
}
export function toOffboardingSchedules(
  dto: OffboardingSchedulesDto,
  company: CompanyId,
  caseId: LifecycleCaseId,
): readonly OffboardingSchedule[] {
  const items = dto.items.map((item) => toOffboardingSchedule(item, company, caseId));
  if (
    items.some((item, index) => item.attempt !== index + 1) ||
    new Set(items.map((item) => item.id)).size !== items.length ||
    new Set(items.map((item) => item.job.id)).size !== items.length ||
    items.filter((item) => item.status === "SCHEDULED").length > 1
  )
    throw new InvalidHttpResponseError();
  return Object.freeze(items);
}
export function toOffboardingScheduleReview(
  dto: OffboardingScheduleReviewDto,
  company: CompanyId,
  caseId: LifecycleCaseId,
): OffboardingScheduleReview {
  const details = toLifecycleCaseDetails(dto.case, company, caseId);
  if (
    details.kind !== "OFFBOARDING" ||
    details.status !== "OPEN" ||
    !isCalendarDate(dto.today) ||
    dto.appliedEmploymentRevision > dto.employmentVersion ||
    new Set(dto.blockers).size !== dto.blockers.length ||
    !scheduleWindowValid(details.targetDate, dto.companyTimezone, dto.scheduledFor, dto.validUntil)
  )
    throw new InvalidHttpResponseError();
  return Object.freeze({ ...dto, case: details, blockers: Object.freeze([...dto.blockers]) });
}
export function toOffboardingScheduleReceipt(
  dto: OffboardingScheduleReceiptDto,
  id: string,
  version: number,
) {
  if (dto.id.toLowerCase() !== id || dto.version !== version) throw new InvalidHttpResponseError();
  return Object.freeze({ id: dto.id.toLowerCase(), version: dto.version });
}
