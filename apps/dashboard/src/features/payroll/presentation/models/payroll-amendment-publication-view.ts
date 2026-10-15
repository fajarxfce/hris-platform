import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollAmendmentSummary } from "../../domain/entities/payroll-amendment";
import type { PayrollAmendmentPublicationDetails } from "../../domain/entities/payroll-amendment-publication-details";
import type { PayrollAmendmentPublicationPreparation } from "../../domain/entities/payroll-amendment-publication-preparation";
import { payrollAmendmentMessages } from "../i18n/payroll-amendment-messages";
import { payrollAmendmentPublicationMessages } from "../i18n/payroll-amendment-publication-messages";
import { payrollAmendmentReviewTotals } from "./payroll-amendment-review-view";

export function payrollAmendmentPublicationSummary(
  amendment: PayrollAmendmentSummary | null,
  locale: Locale,
) {
  const text = payrollAmendmentMessages(locale);
  return amendment
    ? [
        { label: text.employee, value: amendment.employee.name },
        { label: text.employeeNumber, value: amendment.employee.employeeNumber },
        { label: text.firstMonth, value: amendment.firstMonth },
        { label: text.status, value: text.statuses[amendment.status] },
        { label: text.currentRevision, value: String(amendment.version) },
      ]
    : [];
}
export function payrollAmendmentPublicationPreparationView(
  preparation: PayrollAmendmentPublicationPreparation | null,
  locale: Locale,
) {
  if (!preparation) return [];
  const text = payrollAmendmentPublicationMessages(locale);
  return [
    ...payrollAmendmentPublicationSummary(preparation.amendment, locale),
    { label: text.attempt, value: String(preparation.number) },
    { label: text.review, value: String(preparation.review.number) },
    { label: text.reviewVersion, value: String(preparation.review.version) },
    { label: text.approvalVersion, value: String(preparation.approvalVersion) },
    { label: text.calculation, value: String(preparation.calculation.number) },
    ...payrollAmendmentReviewTotals(preparation.calculation, locale),
  ];
}
export function payrollAmendmentPublicationRows(
  attempts: readonly PayrollAmendmentPublicationDetails[],
  locale: Locale,
) {
  const text = payrollAmendmentPublicationMessages(locale),
    time = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: "UTC",
    });
  return attempts.map(({ publication, job }) => ({
    id: publication.id,
    actionLabel: `${text.attempt} ${publication.number}`,
    cells: [
      String(publication.number),
      text.statuses[publication.status],
      text.jobs[job.status],
      time.format(new Date(publication.requestedAt)),
      publication.reason,
    ],
  }));
}
export function payrollAmendmentPublicationView(
  details: PayrollAmendmentPublicationDetails,
  locale: Locale,
) {
  const { publication, job } = details,
    text = payrollAmendmentPublicationMessages(locale),
    time = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: "UTC",
    });
  return {
    published: publication.status === "PUBLISHED",
    stopped: publication.status === "STOPPED",
    exhausted: publication.status === "PENDING" && ["FAILED", "CANCELLED"].includes(job.status),
    failure:
      publication.failureCode || job.failureCode
        ? {
            code: publication.failureCode ?? job.failureCode ?? "job_failed",
            fields: {},
            parameters: {},
          }
        : null,
    properties: [
      { label: text.attempt, value: String(publication.number) },
      { label: text.status, value: text.statuses[publication.status] },
      { label: text.requestedAt, value: time.format(new Date(publication.requestedAt)) },
      {
        label: text.completedAt,
        value: publication.completedAt ? time.format(new Date(publication.completedAt)) : text.none,
      },
      { label: text.reason, value: publication.reason },
      { label: text.initiator, value: publication.actorId },
      { label: text.completedBy, value: publication.completedBy ?? text.none },
    ],
    basis: [
      { label: text.amendmentRevision, value: String(publication.amendmentVersion) },
      { label: text.review, value: publication.reviewId },
      { label: text.reviewVersion, value: String(publication.reviewVersion) },
      { label: text.approvalVersion, value: String(publication.approvalVersion) },
      { label: text.calculation, value: publication.calculationId },
    ],
    progress: [
      { label: text.status, value: text.jobs[job.status] },
      { label: text.completedItems, value: `${job.completedItems} / ${job.totalItems}` },
      { label: text.attempts, value: String(job.attempts) },
      { label: text.pendingCancellation, value: job.cancellationRequested ? text.yes : text.no },
    ],
  };
}
