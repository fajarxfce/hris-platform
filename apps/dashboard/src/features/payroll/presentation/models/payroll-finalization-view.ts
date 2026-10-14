import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollFinalization } from "../../domain/entities/payroll-finalization";
import type { PayrollFinalizationDetails } from "../../domain/entities/payroll-finalization-details";
import type { PayrollFinalizationPreparation } from "../../domain/entities/payroll-finalization-preparation";
import type { PayrollRun } from "../../domain/entities/payroll-run";
import { payrollFinalizationMessages } from "../i18n/payroll-finalization-messages";
import { payrollReviewTotalsView } from "./payroll-review-view";
export function payrollPublicationRows(runs: readonly PayrollRun[], locale: Locale) {
  const text = payrollFinalizationMessages(locale);
  return runs.map((run) => ({
    id: run.id,
    actionLabel: `${text.run} ${run.number} · ${run.earningsMonth}`,
    cells: [
      run.earningsMonth,
      run.reviewReference,
      run.totalEmployees.toLocaleString(locale),
      run.status === "FINALIZED" ? text.runStatuses.FINALIZED : text.runStatuses.CALCULATED,
    ],
  }));
}
export function payrollFinalizationPreparationView(
  preparation: PayrollFinalizationPreparation,
  locale: Locale,
) {
  const text = payrollFinalizationMessages(locale),
    { run, totals } = preparation;
  return [
    { label: text.month, value: run.earningsMonth },
    { label: text.number, value: String(run.number) },
    { label: text.reference, value: run.reviewReference },
    ...payrollReviewTotalsView(totals, locale),
  ];
}
export function payrollFinalizationRows(
  items: readonly PayrollFinalization[],
  locale: Locale,
  timezone: string,
) {
  const text = payrollFinalizationMessages(locale),
    time = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: timezone,
    });
  return items.map((item) => ({
    id: item.id,
    actionLabel: `${text.publication} ${item.number}`,
    cells: [
      String(item.number),
      time.format(new Date(item.requestedAt)),
      item.publishedAt === null ? text.notPublished : time.format(new Date(item.publishedAt)),
      item.reason,
    ],
  }));
}
export function payrollFinalizationView(details: PayrollFinalizationDetails, locale: Locale) {
  const text = payrollFinalizationMessages(locale),
    { finalization, job, run } = details,
    time = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: run.timezone,
    });
  return {
    published: finalization.publishedAt !== null,
    properties: [
      { label: text.month, value: run.earningsMonth },
      { label: text.reference, value: run.reviewReference },
      { label: text.status, value: text.jobStatuses[job.status] },
      { label: text.attempt, value: String(finalization.number) },
      { label: text.issuer, value: `${finalization.companyCode} · ${finalization.companyName}` },
      { label: text.requested, value: time.format(new Date(finalization.requestedAt)) },
      {
        label: text.published,
        value:
          finalization.publishedAt === null
            ? text.none
            : time.format(new Date(finalization.publishedAt)),
      },
      { label: text.reason, value: finalization.reason },
    ],
    totals: payrollReviewTotalsView(details.totals, locale),
    jobFailure:
      job.status === "FAILED" && job.failureCode
        ? { code: job.failureCode, fields: {}, parameters: {} }
        : null,
  };
}
