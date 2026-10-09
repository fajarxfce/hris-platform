import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { BackgroundJob } from "../../domain/entities/background-job";
import type { JobPage } from "../../domain/entities/job-page";
import { canRequestJobCancellation } from "../../domain/policies/job-policy";
import { jobKindLabel, jobMessages } from "../i18n/job-messages";

export function jobListView(page: JobPage, locale: Locale) {
  const text = jobMessages(locale);
  const number = new Intl.NumberFormat(locale);
  const date = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "medium",
    timeZone: "UTC",
  });
  return page.items.map((job) => ({
    id: job.id,
    cells: [
      jobKindLabel(job.kind, locale),
      job.cancellationRequested && (job.status === "QUEUED" || job.status === "RUNNING")
        ? text.requested
        : text[job.status],
      `${number.format(job.completedItems)} / ${number.format(job.totalItems)}`,
      date.format(new Date(job.createdAt)),
    ],
  }));
}
export function jobDetailView(job: BackgroundJob, locale: Locale) {
  const text = jobMessages(locale);
  const number = new Intl.NumberFormat(locale);
  const date = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "medium",
    timeZone: "UTC",
  });
  return {
    canCancel: canRequestJobCancellation(job),
    cancellationPending:
      job.cancellationRequested && (job.status === "QUEUED" || job.status === "RUNNING"),
    items: [
      { label: text.id, value: job.id },
      { label: text.kind, value: jobKindLabel(job.kind, locale) },
      { label: text.status, value: text[job.status] },
      {
        label: text.progress,
        value: `${number.format(job.completedItems)} / ${number.format(job.totalItems)}`,
      },
      {
        label: text.progressMode,
        value: job.progressMode === "FIXED_TOTAL" ? text.fixedTotal : text.upperBound,
      },
      { label: text.attempts, value: number.format(job.attempts) },
      { label: text.created, value: date.format(new Date(job.createdAt)) },
      {
        label: text.scheduled,
        value: job.scheduledFor === null ? text.none : date.format(new Date(job.scheduledFor)),
      },
      { label: text.available, value: date.format(new Date(job.availableAt)) },
      {
        label: text.finished,
        value: job.finishedAt === null ? text.none : date.format(new Date(job.finishedAt)),
      },
      { label: text.version, value: number.format(job.version) },
      ...(job.failureCode === null
        ? []
        : [
            {
              label: text.failure,
              value: failureMessage({ code: job.failureCode, fields: {}, parameters: {} }, locale),
            },
            { label: text.failureCode, value: job.failureCode },
          ]),
    ],
  };
}
export type JobDetailView = ReturnType<typeof jobDetailView>;
