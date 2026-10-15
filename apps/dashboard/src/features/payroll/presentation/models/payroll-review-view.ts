import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollReview } from "../../domain/entities/payroll-review";
import type { PayrollReviewDetails } from "../../domain/entities/payroll-review-details";
import type { PayrollReviewPreparation } from "../../domain/entities/payroll-review-preparation";
import type { PayrollReviewTotals } from "../../domain/entities/payroll-review-totals";
import { payrollReviewMessages } from "../i18n/payroll-review-messages";
import { payrollAmountView } from "./payroll-policy-view";
import { payrollReviewStagesView } from "./payroll-review-stages-view";
export function payrollReviewTotalsView(totals: PayrollReviewTotals, locale: Locale) {
  const text = payrollReviewMessages(locale);
  return [
    { label: text.employees, value: totals.employeeCount.toLocaleString(locale) },
    { label: text.gross, value: payrollAmountView(totals.taxableGross, locale) },
    { label: text.withheld, value: payrollAmountView(totals.withheld, locale) },
    { label: text.takeHome, value: payrollAmountView(totals.takeHome, locale) },
  ];
}
export function payrollReviewPreparationView(
  preparation: PayrollReviewPreparation,
  locale: Locale,
) {
  const text = payrollReviewMessages(locale),
    { run, totals } = preparation;
  return [
    { label: text.month, value: run.earningsMonth },
    { label: text.runNumber, value: String(run.number) },
    { label: text.reference, value: run.reviewReference },
    { label: text.roster, value: run.totalEmployees.toLocaleString(locale) },
    ...payrollReviewTotalsView(totals, locale),
  ];
}
export function payrollReviewRows(
  reviews: readonly PayrollReview[],
  locale: Locale,
  timezone: string,
) {
  const text = payrollReviewMessages(locale),
    time = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: timezone,
    });
  return reviews.map((review) => ({
    id: review.id,
    actionLabel: `${text.review} ${review.number}`,
    cells: [
      String(review.number),
      text.statuses[review.status],
      time.format(new Date(review.submittedAt)),
      review.reason,
    ],
  }));
}
export function payrollReviewView(details: PayrollReviewDetails, locale: Locale) {
  const text = payrollReviewMessages(locale),
    { review, run, approval } = details,
    time = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: run.timezone,
    }),
    names = new Map(details.accounts.map((account) => [account.id, account.displayName]));
  return {
    properties: [
      { label: text.month, value: run.earningsMonth },
      { label: text.runNumber, value: String(run.number) },
      { label: text.status, value: text.statuses[approval.status] },
      { label: text.stage, value: String(approval.currentStep + 1) },
      {
        label: text.assignees,
        value:
          approval.stages[approval.currentStep]?.map((id) => names.get(id) ?? id).join(", ") ||
          text.none,
      },
      { label: text.reference, value: run.reviewReference },
      { label: text.referenceDate, value: review.referenceDate },
      { label: text.submittedBy, value: names.get(review.submittedBy) ?? review.submittedBy },
      { label: text.submitted, value: time.format(new Date(review.submittedAt)) },
      { label: text.reason, value: review.reason },
    ],
    totals: payrollReviewTotalsView(review.totals, locale),
    stages: payrollReviewStagesView(approval, names, locale),
    history: details.changes.map((change) => ({
      id: String(change.revision),
      cells: [
        text.actions[change.action],
        text.statuses[change.status],
        change.step === null ? text.none : String(change.step + 1),
        names.get(change.actorId) ?? change.actorId,
        change.decidingFor && change.decidingFor !== change.actorId
          ? (names.get(change.decidingFor) ?? change.decidingFor)
          : text.none,
        time.format(new Date(change.at)),
        change.reason || text.none,
      ],
    })),
  };
}
