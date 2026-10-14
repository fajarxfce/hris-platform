import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollCalculationReview } from "../../domain/entities/payroll-calculation-review";
import type { PayrollRun } from "../../domain/entities/payroll-run";
import type { PayrollRunDetails } from "../../domain/entities/payroll-run-details";
import { payrollCalculationMessages } from "../i18n/payroll-calculation-messages";
import { payrollAmountView } from "./payroll-policy-view";
export function payrollCalculationReviewView(review: PayrollCalculationReview, locale: Locale) {
  const text = payrollCalculationMessages(locale),
    { period, source, policy } = review;
  return [
    { label: text.month, value: period.earningsMonth },
    { label: text.payment, value: period.plannedPaymentDate },
    { label: text.employees, value: period.participantCount.toLocaleString(locale) },
    { label: text.periodVersion, value: String(period.version) },
    { label: text.work, value: source?.closed ? text.closed : text.notClosed },
    { label: text.workVersion, value: source ? String(source.version) : "—" },
    {
      label: text.policyRevision,
      value: policy ? String(policy.appliedRevision) : text.notConfigured,
    },
    { label: text.policyVersion, value: policy ? String(policy.version) : "—" },
    { label: text.rule, value: policy?.incomeTaxRuleId ?? "—" },
    { label: text.insuranceRule, value: policy?.insuranceRuleId ?? "—" },
    { label: text.timezone, value: period.timezone },
  ];
}
export function payrollRunProperties(run: PayrollRun, locale: Locale) {
  const text = payrollCalculationMessages(locale);
  return [
    { label: text.month, value: run.earningsMonth },
    { label: text.taxMonth, value: run.taxMonth },
    { label: text.due, value: run.incomeDueDate },
    { label: text.payment, value: run.plannedPaymentDate },
    { label: text.status, value: text.runStatuses[run.status] },
    { label: text.version, value: String(run.version) },
    { label: text.workVersion, value: String(run.workPeriodVersion) },
    { label: text.policyRevision, value: String(run.policyRevision) },
    { label: text.reference, value: run.reviewReference },
    { label: text.reason, value: run.reason },
    { label: text.timezone, value: run.timezone },
  ];
}
export function payrollRunRows(runs: readonly PayrollRun[], locale: Locale, timezone: string) {
  const text = payrollCalculationMessages(locale),
    time = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: timezone,
    });
  return runs.map((run) => ({
    id: run.id,
    actionLabel: `${text.run} ${run.number}`,
    cells: [
      String(run.number),
      time.format(new Date(run.createdAt)),
      text.runStatuses[run.status],
      `${run.processed}/${run.totalEmployees}`,
      String(run.failed),
    ],
  }));
}
export function payrollRunView(details: PayrollRunDetails, locale: Locale) {
  const text = payrollCalculationMessages(locale),
    { run } = details,
    time = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: run.timezone,
    });
  return {
    properties: payrollRunProperties(run, locale),
    progress: run.processed / run.totalEmployees,
    progressLabel: `${run.processed.toLocaleString(locale)} / ${run.totalEmployees.toLocaleString(locale)}`,
    metrics: [
      { label: text.employees, value: run.totalEmployees.toLocaleString(locale) },
      { label: text.processed, value: run.processed.toLocaleString(locale) },
      { label: text.succeeded, value: run.succeeded.toLocaleString(locale) },
      { label: text.failed, value: run.failed.toLocaleString(locale) },
    ],
    failure: details.job.failureCode
      ? { code: details.job.failureCode, fields: {}, parameters: {} }
      : null,
    results: details.results.items.map((item) => ({
      id: item.target.employeeId,
      actionLabel: item.target.employeeName,
      cells: [
        item.target.employeeNumber,
        item.target.employeeName,
        item.failure ? failureMessage(item.failure, locale) : text.succeeded,
        ...[item.taxableGross, item.withheld, item.takeHome].map((value) =>
          value === null ? "—" : payrollAmountView(value, locale),
        ),
      ],
    })),
    attempts: details.attempts.map((attempt) => ({
      id: attempt.jobId,
      cells: [
        String(attempt.number),
        time.format(new Date(attempt.startedAt)),
        String(attempt.baseCompleted),
        attempt.reason,
        attempt.jobId,
      ],
    })),
  };
}
