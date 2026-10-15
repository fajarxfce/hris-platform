import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { OffboardingSchedule } from "../../domain/entities/offboarding-schedule";
import type { OffboardingScheduleReview } from "../../domain/entities/offboarding-schedule-review";
import { lifecycleCaseMessages } from "../i18n/lifecycle-case-messages";
import { offboardingScheduleMessages } from "../i18n/offboarding-schedule-messages";

export function offboardingScheduleStatus(details: OffboardingSchedule, locale: Locale): string {
  const text = offboardingScheduleMessages(locale);
  if (details.status !== "SCHEDULED") return text[details.status];
  if (["FAILED", "CANCELLED"].includes(details.job.status)) return text.needsRecovery;
  if (details.job.cancellationRequested) return text.cancelling;
  if (details.job.status === "RUNNING") return text.running;
  return text.SCHEDULED;
}
export function offboardingSchedulesView(items: readonly OffboardingSchedule[], locale: Locale) {
  const number = new Intl.NumberFormat(locale);
  return items.map((item) => {
    const instant = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: item.companyTimezone,
    });
    return {
      id: item.id,
      actionLabel: number.format(item.attempt),
      cells: [
        number.format(item.attempt),
        offboardingScheduleStatus(item, locale),
        `${instant.format(new Date(item.scheduledFor))} (${item.companyTimezone})`,
        instant.format(new Date(item.createdAt)),
        item.reason,
      ],
    };
  });
}
export function offboardingScheduleView(details: OffboardingSchedule, locale: Locale) {
  const text = offboardingScheduleMessages(locale),
    number = new Intl.NumberFormat(locale),
    date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" }),
    instant = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: details.companyTimezone,
    });
  return {
    properties: [
      { label: text.status, value: offboardingScheduleStatus(details, locale) },
      { label: text.attempt, value: number.format(details.attempt) },
      {
        label: text.lastWorkingDate,
        value: date.format(new Date(`${details.lastWorkingDate}T00:00:00Z`)),
      },
      { label: text.scheduledFor, value: instant.format(new Date(details.scheduledFor)) },
      { label: text.validUntil, value: instant.format(new Date(details.validUntil)) },
      { label: text.timezone, value: details.companyTimezone },
      { label: text.requestedAt, value: instant.format(new Date(details.createdAt)) },
      { label: text.reason, value: details.reason },
    ],
    job: [
      { label: text.jobStatus, value: text[details.job.status] },
      { label: text.attempts, value: number.format(details.job.attempts) },
      ...(details.job.finishedAt
        ? [{ label: text.finishedAt, value: instant.format(new Date(details.job.finishedAt)) }]
        : []),
    ],
    references: [
      { label: text.requestedBy, value: details.createdBy },
      { label: text.caseVersion, value: number.format(details.caseVersion) },
      { label: text.employmentVersion, value: number.format(details.employmentVersion) },
      { label: text.appliedRevision, value: number.format(details.appliedEmploymentRevision) },
      { label: text.scheduleId, value: details.id },
      { label: text.jobVersion, value: number.format(details.job.version) },
      { label: text.jobId, value: details.job.id },
    ],
    issue:
      details.status === "FAILED" || details.job.status === "FAILED"
        ? ({
            code: details.failureCode ?? details.job.failureCode ?? "offboarding_execution_failed",
            fields: {},
            parameters: {},
          } as Failure)
        : null,
  };
}
export function offboardingScheduleReviewView(review: OffboardingScheduleReview, locale: Locale) {
  const text = offboardingScheduleMessages(locale),
    shared = lifecycleCaseMessages(locale),
    date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" }),
    instant = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: review.companyTimezone,
    });
  return {
    properties: [
      { label: shared.employee, value: review.case.employee.name },
      { label: shared.employeeNumber, value: review.case.employee.employeeNumber },
      {
        label: text.lastWorkingDate,
        value: date.format(new Date(`${review.case.targetDate}T00:00:00Z`)),
      },
      { label: text.companyDate, value: date.format(new Date(`${review.today}T00:00:00Z`)) },
      { label: text.scheduledFor, value: instant.format(new Date(review.scheduledFor)) },
      { label: text.validUntil, value: instant.format(new Date(review.validUntil)) },
      { label: text.timezone, value: review.companyTimezone },
    ],
    issues: review.blockers.map((code): Failure => ({ code, fields: {}, parameters: {} })),
  };
}
