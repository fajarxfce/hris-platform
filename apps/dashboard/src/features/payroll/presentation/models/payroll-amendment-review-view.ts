import type { Locale } from "../../../../core/presentation/i18n/messages";
import type {
  PayrollAmendmentDetails,
  PayrollAmendmentReviewSummary,
} from "../../domain/entities/payroll-amendment";
import {
  type PayrollAmendmentCalculationSummary,
  payrollAmendmentDifferenceFields,
} from "../../domain/entities/payroll-amendment-calculation";
import type { PayrollAmendmentReviewDetails } from "../../domain/entities/payroll-amendment-review-details";
import { payrollAmendmentMessages } from "../i18n/payroll-amendment-messages";
import { payrollAmendmentReviewMessages } from "../i18n/payroll-amendment-review-messages";
import { payrollReviewMessages } from "../i18n/payroll-review-messages";
import { payrollAmendmentActionView } from "./payroll-amendment-action-view";
import { payrollAmountView } from "./payroll-policy-view";
import { payrollReviewStagesView } from "./payroll-review-stages-view";

export function payrollAmendmentReviewTotals(
  calculation: PayrollAmendmentCalculationSummary | null,
  locale: Locale,
) {
  const text = payrollAmendmentMessages(locale);
  return calculation?.difference
    ? payrollAmendmentDifferenceFields.map((key) => ({
        label: text[key],
        value: payrollAmountView(calculation.difference?.[key] ?? "0", locale),
      }))
    : [];
}
export function payrollAmendmentReviewPreparationView(
  details: PayrollAmendmentDetails | null,
  locale: Locale,
) {
  return details
    ? [
        ...payrollAmendmentActionView(details, locale),
        ...payrollAmendmentReviewTotals(details.latestCalculation, locale),
      ]
    : [];
}
export function payrollAmendmentReviewRows(
  reviews: readonly PayrollAmendmentReviewSummary[],
  locale: Locale,
) {
  const text = payrollReviewMessages(locale),
    amendment = payrollAmendmentReviewMessages(locale);
  return reviews.map((review) => ({
    id: review.id,
    actionLabel: `${amendment.review} ${review.number}`,
    cells: [
      String(review.number),
      text.statuses[review.status],
      payrollAmountView(review.exposure, locale),
      `${new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short", timeZone: review.timezone }).format(new Date(review.submittedAt))} (${review.timezone})`,
      review.reason,
    ],
  }));
}
export function payrollAmendmentReviewView(details: PayrollAmendmentReviewDetails, locale: Locale) {
  const text = payrollReviewMessages(locale),
    amendmentText = payrollAmendmentMessages(locale),
    { amendment, review, approval } = details,
    names = new Map(details.accounts.map((account) => [account.id, account.displayName])),
    time = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: review.timezone,
    });
  return {
    properties: [
      { label: amendmentText.employee, value: amendment.employee.name },
      { label: amendmentText.employeeNumber, value: amendment.employee.employeeNumber },
      { label: amendmentText.firstMonth, value: amendment.firstMonth },
      { label: text.status, value: text.statuses[review.status] },
      { label: text.stage, value: String(approval.currentStep + 1) },
      {
        label: text.assignees,
        value:
          approval.stages[approval.currentStep]?.map((id) => names.get(id) ?? id).join(", ") ||
          text.none,
      },
      { label: amendmentText.reviewedRevision, value: String(review.amendmentVersion) },
      { label: text.referenceDate, value: review.referenceDate },
      { label: text.submittedBy, value: names.get(review.submittedBy) ?? review.submittedBy },
      {
        label: `${text.submitted} (${review.timezone})`,
        value: time.format(new Date(review.submittedAt)),
      },
      { label: text.reason, value: review.reason },
    ],
    totals: [
      { label: amendmentText.exposure, value: payrollAmountView(review.exposure, locale) },
      ...payrollAmendmentReviewTotals(details.calculation, locale),
    ],
    stages: payrollReviewStagesView(approval, names, locale),
    history: details.history.map((change) => ({
      id: String(change.amendmentRevision),
      actionLabel: `${amendmentText.revision} ${change.amendmentRevision}`,
      cells: [
        String(change.amendmentRevision),
        text.actions[change.action],
        text.statuses[change.status],
        change.step === null ? text.none : String(change.step + 1),
        names.get(change.actorId) ?? change.actorId,
        change.decidingFor && change.decidingFor !== change.actorId
          ? (names.get(change.decidingFor) ?? change.decidingFor)
          : text.none,
        time.format(new Date(change.recordedAt)),
        change.reason,
      ],
    })),
  };
}
