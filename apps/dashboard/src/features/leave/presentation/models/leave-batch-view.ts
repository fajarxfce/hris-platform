import type { Failure } from "../../../../core/domain/result";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { LeaveBatch } from "../../domain/entities/leave-batch";
import type { LeaveBatchDetails } from "../../domain/entities/leave-batch-details";
import type { LeaveBatchReview } from "../../domain/entities/leave-batch-review";
import type { LeavePolicyTerms } from "../../domain/entities/leave-policy-terms";
import { leaveBatchMessages } from "../i18n/leave-batch-messages";
export function leaveBatchPeriodLabel(
  period: string,
  kind: LeaveBatch["kind"],
  locale: Locale,
): string {
  return kind === "YEAR_CLOSE"
    ? period.slice(0, 4)
    : new Intl.DateTimeFormat(locale, { month: "long", year: "numeric", timeZone: "UTC" }).format(
        new Date(`${period}-01T00:00:00Z`),
      );
}
export function leaveBatchesView(items: readonly LeaveBatch[], locale: Locale) {
  const text = leaveBatchMessages(locale),
    number = new Intl.NumberFormat(locale);
  return items.map((item) => ({
    id: item.id,
    actionLabel: `${item.policy.name} · ${leaveBatchPeriodLabel(item.period, item.kind, locale)}`,
    cells: [
      item.policy.name,
      text[item.kind],
      leaveBatchPeriodLabel(item.period, item.kind, locale),
      item.status === "RUNNING" ? text.incomplete : text[item.status],
      number.format(item.totalEmployees),
      new Intl.DateTimeFormat(locale, {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: item.timezone,
      }).format(new Date(item.createdAt)),
    ],
  }));
}
export function leaveBatchPolicyView(policy: LeavePolicyTerms, locale: Locale) {
  const text = leaveBatchMessages(locale),
    number = new Intl.NumberFormat(locale);
  return [
    {
      label: text.frequency,
      value: policy.accrual ? text[policy.accrual.frequency] : text.noAccrual,
    },
    ...(policy.accrual
      ? [
          { label: text.days, value: number.format(Number(policy.accrual.daysPerPeriod)) },
          { label: text.carryLimit, value: number.format(Number(policy.accrual.carryLimitDays)) },
        ]
      : []),
    { label: text.serviceMonths, value: number.format(policy.minServiceMonths) },
    {
      label: text.contracts,
      value: policy.allowedContracts.map((contract) => text[contract]).join(", "),
    },
  ];
}
export function leaveBatchReviewView(review: LeaveBatchReview, locale: Locale) {
  const text = leaveBatchMessages(locale),
    number = new Intl.NumberFormat(locale);
  return {
    properties: [
      { label: text.type, value: review.type.name },
      { label: text.kind, value: text[review.kind] },
      { label: text.period, value: leaveBatchPeriodLabel(review.period, review.kind, locale) },
      { label: text.employees, value: number.format(review.employeeIds.length) },
      {
        label: text.companyDate,
        value: new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" }).format(
          new Date(`${review.companyDate}T00:00:00Z`),
        ),
      },
      { label: text.timezone, value: review.timezone },
    ],
    policy: leaveBatchPolicyView(review.type, locale),
  };
}
export function leaveBatchView(details: LeaveBatchDetails, locale: Locale) {
  const text = leaveBatchMessages(locale),
    batch = details.batch,
    number = new Intl.NumberFormat(locale),
    instant = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: batch.timezone,
    });
  return {
    properties: [
      { label: text.type, value: batch.policy.name },
      { label: text.kind, value: text[batch.kind] },
      { label: text.period, value: leaveBatchPeriodLabel(batch.period, batch.kind, locale) },
      {
        label: text.status,
        value:
          details.job.cancellationRequested && ["QUEUED", "RUNNING"].includes(details.job.status)
            ? text.cancelling
            : text[details.job.status],
      },
      {
        label: text.completed,
        value: `${number.format(details.counts.completed)} / ${number.format(batch.totalEmployees)}`,
      },
      { label: text.createdAt, value: instant.format(new Date(batch.createdAt)) },
      { label: text.timezone, value: batch.timezone },
      { label: text.reason, value: batch.reason },
    ],
    metrics: [
      { label: text.APPLIED, value: number.format(details.counts.applied) },
      { label: text.UNCHANGED, value: number.format(details.counts.unchanged) },
      { label: text.SKIPPED, value: number.format(details.counts.skipped) },
      { label: text.FAILED, value: number.format(details.counts.failed) },
    ],
    rows: details.results.items.map((item) => ({
      id: item.employee.id,
      actionLabel: item.employee.name ?? item.employee.number ?? text.noName,
      cells: [
        item.employee.name ?? text.noName,
        item.employee.number ?? "—",
        text[item.status],
        item.failureCode
          ? failureMessage(
              { code: item.failureCode, fields: {}, parameters: item.parameters },
              locale,
            )
          : "—",
      ],
    })),
    attempts: details.attempts.map((item) => ({
      id: item.jobId,
      cells: [
        number.format(item.number),
        instant.format(new Date(item.startedAt)),
        number.format(item.baseCompleted),
        item.reason,
      ],
    })),
    policy: leaveBatchPolicyView(batch.policy, locale),
    references: [
      { label: text.policyRevision, value: number.format(batch.policy.revision) },
      { label: text.policyHead, value: number.format(batch.policyVersion) },
      { label: text.batchReference, value: batch.id },
      { label: text.jobReference, value: details.job.id },
      { label: text.actorReference, value: batch.actorId },
    ],
    issue: details.job.failureCode
      ? ({ code: details.job.failureCode, fields: {}, parameters: {} } as Failure)
      : null,
  };
}
