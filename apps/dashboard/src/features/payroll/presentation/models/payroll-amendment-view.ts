import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollAmendmentSummary } from "../../domain/entities/payroll-amendment";
import { payrollAmendmentDifferenceFields } from "../../domain/entities/payroll-amendment-calculation";
import type { PayrollAmendmentInspection } from "../../domain/entities/payroll-amendment-search";
import { payrollAmendmentEditorMessages } from "../i18n/payroll-amendment-editor-messages";
import { payrollAmendmentMessages } from "../i18n/payroll-amendment-messages";
import { payrollInputMessages } from "../i18n/payroll-input-messages";
import { compensationTermsView } from "./compensation-view";
import { payrollAmendmentAttendanceView } from "./payroll-amendment-attendance-view";
import { payrollCalculationOutputView } from "./payroll-calculation-output-view";
import { payrollInputTermsView } from "./payroll-input-view";
import { payrollAmountView } from "./payroll-policy-view";
export function payrollAmendmentRows(
  items: readonly PayrollAmendmentSummary[],
  locale: Locale,
  timezone: string,
) {
  const text = payrollAmendmentMessages(locale),
    time = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: timezone,
    });
  return items.map((row) => ({
    id: row.id,
    actionLabel: `${row.employee.name} · ${row.firstMonth} · ${text.revision} ${row.version}`,
    cells: [
      row.employee.name,
      row.employee.employeeNumber,
      row.firstMonth,
      text.statuses[row.status],
      String(row.version),
      time.format(new Date(row.recordedAt)),
    ],
  }));
}
export function payrollAmendmentInspectionView(
  data: PayrollAmendmentInspection,
  locale: Locale,
  timezone: string,
  selectedAssessment: string | null,
) {
  const text = payrollAmendmentMessages(locale),
    inputText = payrollInputMessages(locale),
    authorText = payrollAmendmentEditorMessages(locale),
    s = data.selected.summary,
    time = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: timezone,
    }),
    attempt = data.calculation,
    preview = attempt?.preview,
    selected =
      preview?.calculations.find((row) => row.source.assessmentId === selectedAssessment) ??
      preview?.calculations[0] ??
      null;
  return {
    title: s.employee.name,
    overview: [
      { label: text.employeeNumber, value: s.employee.employeeNumber },
      { label: text.firstMonth, value: s.firstMonth },
      { label: text.revision, value: String(s.version) },
      { label: text.currentRevision, value: String(data.current.amendment.summary.version) },
      { label: text.status, value: text.statuses[s.status] },
      { label: text.action, value: text.actions[s.action] },
      { label: `${text.recorded} (${timezone})`, value: time.format(new Date(s.recordedAt)) },
      { label: text.actor, value: s.actorId },
      { label: text.reason, value: s.reason },
    ],
    history: data.history.items.map((row) => ({
      id: String(row.version),
      actionLabel: `${text.revision} ${row.version}`,
      cells: [
        String(row.version),
        text.actions[row.action],
        text.statuses[row.status],
        time.format(new Date(row.recordedAt)),
        row.reason,
      ],
    })),
    sources: data.selected.terms.sourceVersions.map((source) => ({
      id: source.assessmentId,
      cells: [source.assessmentId, String(source.revision)],
    })),
    corrections: data.selected.terms.corrections.map((correction, index) => {
      const month =
        preview?.calculations.find((row) => row.source.assessmentId === correction.assessmentId)
          ?.month ?? (correction.assessmentId === s.firstAssessmentId ? s.firstMonth : null);
      const t = correction.input;
      return {
        id: correction.assessmentId,
        label: month ?? `${text.correction} ${index + 1}`,
        attendance: [
          {
            label: authorText.attendanceSource,
            value: correction.workAdjustmentId ?? authorText.keepAttendance,
          },
        ],
        compensation: correction.compensation
          ? compensationTermsView(correction.compensation, locale)
          : null,
        input: t ? payrollInputTermsView(t, locale) : null,
        inputProperties: t
          ? [
              {
                label: inputText.nonCashTaxable,
                value: payrollAmountView(t.nonCashTaxable, locale),
              },
              {
                label: inputText.scheduledMonthUnits,
                value: t.scheduledMonthUnits ?? inputText.notSpecified,
              },
              { label: inputText.reviewReference, value: t.reviewReference },
            ]
          : [],
      };
    }),
    review: data.current.latestReview
      ? [
          { label: text.reviewNumber, value: String(data.current.latestReview.number) },
          {
            label: text.reviewStatus,
            value: text.reviewStatuses[data.current.latestReview.status],
          },
          {
            label: text.reviewedRevision,
            value: String(data.current.latestReview.amendmentVersion),
          },
          {
            label: text.exposure,
            value: payrollAmountView(data.current.latestReview.exposure, locale),
          },
          { label: text.reason, value: data.current.latestReview.reason },
        ]
      : [],
    attempt: attempt
      ? [
          { label: text.attempt, value: String(attempt.summary.number) },
          { label: text.status, value: text.outcomes[attempt.summary.status] },
          { label: text.basis, value: String(attempt.summary.draftVersion) },
          {
            label: `${text.recorded} (${timezone})`,
            value: time.format(new Date(attempt.summary.recordedAt)),
          },
          { label: text.reason, value: attempt.summary.reason },
        ]
      : [],
    totals: preview
      ? payrollAmendmentDifferenceFields.map((key) => ({
          label: text[key],
          value: payrollAmountView(preview.difference[key], locale),
        }))
      : [],
    months:
      preview?.calculations.map((row) => ({
        id: row.source.assessmentId,
        actionLabel: row.month,
        cells: [
          row.month,
          payrollAmountView(row.original.tax.takeHome, locale),
          payrollAmountView(row.calculation.tax.takeHome, locale),
          payrollAmountView(row.difference.takeHome, locale),
        ],
      })) ?? [],
    selectedMonth: selected?.month ?? null,
    attendance: selected
      ? payrollAmendmentAttendanceView(
          selected.month,
          selected.workAdjustmentId,
          selected.workDays,
          locale,
        )
      : null,
    selectedSource: selected
      ? `${selected.source.assessmentId} · ${text.sourceRevision} ${selected.source.revision}`
      : null,
    comparison: selected
      ? payrollAmendmentDifferenceFields.map((key) => ({
          id: key,
          cells: [
            text[key],
            payrollAmountView(
              key === "employeeDeductions" || key === "employerCost"
                ? selected.original[key]
                : selected.original.tax[key],
              locale,
            ),
            payrollAmountView(
              key === "employeeDeductions" || key === "employerCost"
                ? selected.calculation[key]
                : selected.calculation.tax[key],
              locale,
            ),
            payrollAmountView(selected.difference[key], locale),
          ],
        }))
      : [],
    original: payrollCalculationOutputView(selected?.original ?? null, locale),
    proposed: payrollCalculationOutputView(selected?.calculation ?? null, locale),
  };
}
