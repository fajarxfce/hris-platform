import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { LifecycleTemplate } from "../../domain/entities/lifecycle-template";
import type { OnboardingSchedule } from "../../domain/entities/onboarding-schedule";
import type { OnboardingScheduleReview } from "../../domain/entities/onboarding-schedule-review";
import { onboardingScheduleMessages } from "../i18n/onboarding-schedule-messages";
import { lifecycleCaseTemplatePreview } from "./lifecycle-case-template-preview";
export function onboardingScheduleStatus(details: OnboardingSchedule, locale: Locale): string {
  const text = onboardingScheduleMessages(locale);
  if (details.status !== "SCHEDULED") return text[details.status];
  if (["FAILED", "CANCELLED"].includes(details.job.status)) return text.needsRecovery;
  if (details.job.cancellationRequested) return text.cancelling;
  if (details.job.status === "RUNNING") return text.running;
  return text.SCHEDULED;
}
export function onboardingSchedulesView(items: readonly OnboardingSchedule[], locale: Locale) {
  const number = new Intl.NumberFormat(locale),
    date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  return items.map((item) => ({
    id: item.id,
    actionLabel: number.format(item.attempt),
    cells: [
      number.format(item.attempt),
      onboardingScheduleStatus(item, locale),
      item.template.name,
      date.format(new Date(`${item.startsOn}T00:00:00Z`)),
      date.format(new Date(`${item.targetDate}T00:00:00Z`)),
    ],
  }));
}
export function onboardingScheduleView(details: OnboardingSchedule, locale: Locale) {
  const text = onboardingScheduleMessages(locale),
    number = new Intl.NumberFormat(locale),
    date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" }),
    instant = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: details.companyTimezone,
    });
  return {
    properties: [
      { label: text.status, value: onboardingScheduleStatus(details, locale) },
      { label: text.template, value: details.template.name },
      { label: text.firstDay, value: date.format(new Date(`${details.targetDate}T00:00:00Z`)) },
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
      { label: text.employmentVersion, value: number.format(details.employmentVersion) },
      { label: text.appliedRevision, value: number.format(details.appliedEmploymentRevision) },
      { label: text.templateVersion, value: number.format(details.template.version) },
      { label: text.scheduleId, value: details.id },
      { label: text.caseId, value: details.caseId },
      { label: text.jobId, value: details.job.id },
      { label: text.jobVersion, value: number.format(details.job.version) },
    ],
    assignees: Object.entries(details.assignees).map(([key, id]) => ({
      label: details.template.tasks.find((task) => task.key === key)?.title ?? key,
      value: id,
    })),
    tasks: onboardingTaskRows(
      details.template,
      details.targetDate,
      Object.fromEntries(Object.keys(details.assignees).map((key) => [key, text.assigned])),
      locale,
    ),
    issue:
      details.status === "FAILED" || details.job.status === "FAILED"
        ? ({
            code: details.failureCode ?? details.job.failureCode ?? "onboarding_execution_failed",
            fields: {},
            parameters: {},
          } as Failure)
        : null,
  };
}
export function onboardingScheduleReviewView(review: OnboardingScheduleReview, locale: Locale) {
  const text = onboardingScheduleMessages(locale),
    date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" }),
    instant = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: review.companyTimezone,
    });
  return {
    properties: [
      { label: text.employee, value: review.employee.name },
      { label: text.employeeNumber, value: review.employee.employeeNumber },
      { label: text.template, value: review.template.name },
      { label: text.firstDay, value: date.format(new Date(`${review.targetDate}T00:00:00Z`)) },
      { label: text.companyDate, value: date.format(new Date(`${review.today}T00:00:00Z`)) },
      { label: text.scheduledFor, value: instant.format(new Date(review.scheduledFor)) },
      { label: text.validUntil, value: instant.format(new Date(review.validUntil)) },
      { label: text.timezone, value: review.companyTimezone },
    ],
    issues: review.blockers.map((code): Failure => ({ code, fields: {}, parameters: {} })),
  };
}
export function onboardingTaskRows(
  template: LifecycleTemplate,
  targetDate: string,
  assignees: Readonly<Record<string, string>>,
  locale: Locale,
) {
  const text = onboardingScheduleMessages(locale);
  return lifecycleCaseTemplatePreview(template, targetDate, locale).map((row) => ({
    ...row,
    actionLabel: row.cells[0] ?? row.id,
    cells: [
      ...row.cells,
      (Object.hasOwn(assignees, row.id) ? assignees[row.id] : null) ?? text.unassigned,
    ],
  }));
}
