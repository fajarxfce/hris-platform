import type { Failure } from "../../../../core/domain/result";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { payrollAmendmentDifferenceFields } from "../../domain/entities/payroll-amendment-calculation";
import type { PayrollAmendmentCandidate } from "../../domain/entities/payroll-amendment-preparation";
import { payrollAmendmentEditorMessages } from "../i18n/payroll-amendment-editor-messages";
import { payrollAmendmentMessages } from "../i18n/payroll-amendment-messages";
import { payrollAmendmentAttendanceView } from "./payroll-amendment-attendance-view";
import type { PayrollAmendmentEditorState } from "./payroll-amendment-editor-state";
import { payrollAmountView } from "./payroll-policy-view";
export function payrollAmendmentCandidateRows(
  items: readonly PayrollAmendmentCandidate[],
  locale: Locale,
  timezone: string,
) {
  const time = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: timezone,
  });
  return items.map((row) => ({
    id: row.assessmentId,
    actionLabel: `${row.employeeName} · ${row.taxMonth}`,
    cells: [
      row.employeeName,
      row.employeeNumber,
      row.taxMonth,
      String(row.revision),
      time.format(new Date(row.publishedAt)),
    ],
  }));
}
export function payrollAmendmentEditorView(state: PayrollAmendmentEditorState, locale: Locale) {
  const { context, terms, preview } = state;
  if (!context || !terms) return null;
  const text = payrollAmendmentEditorMessages(locale),
    read = payrollAmendmentMessages(locale),
    preparation = context.preparation,
    first = preparation.assessments.find(
      (row) => row.source.assessmentId === preparation.firstAssessmentId,
    );
  return {
    title: first?.employee.name ?? text.editor,
    properties: [
      { label: read.employeeNumber, value: first?.employee.employeeNumber ?? "" },
      { label: text.first, value: first?.month ?? "" },
      {
        label: read.revision,
        value: context.current ? String(context.current.summary.version) : "—",
      },
    ],
    rows: preparation.assessments.map((row) => {
      const correction = terms.corrections.find(
          (item) => item.assessmentId === row.source.assessmentId,
        ),
        observed = context.current?.terms.sourceVersions.find(
          (item) => item.assessmentId === row.source.assessmentId,
        );
      return {
        id: row.source.assessmentId,
        actionLabel: row.month,
        actionDisabled: first === undefined || row.month < first.month,
        cells: [
          row.month,
          row.employee.name,
          String(row.source.revision),
          observed ? String(observed.revision) : "—",
          payrollAmountView(row.calculation.tax.takeHome, locale),
          [
            correction?.compensation ? text.proposedCompensation : null,
            correction?.input ? text.proposedInput : null,
            correction?.workAdjustmentId ? text.proposedAttendance : null,
          ]
            .filter(Boolean)
            .join(", ") || text.unchanged,
        ],
      };
    }),
    totals: preview
      ? payrollAmendmentDifferenceFields.map((field) => ({
          label: read[field],
          value: payrollAmountView(preview.difference[field], locale),
        }))
      : [],
    comparison:
      preview?.calculations.map((row) => ({
        id: row.source.assessmentId,
        cells: [
          row.month,
          payrollAmountView(row.difference.taxableGross, locale),
          payrollAmountView(row.difference.withheld, locale),
          payrollAmountView(row.original.tax.takeHome, locale),
          payrollAmountView(row.calculation.tax.takeHome, locale),
          payrollAmountView(row.difference.takeHome, locale),
        ],
      })) ?? [],
    attendance:
      preview?.calculations.map((row) => ({
        id: row.source.assessmentId,
        view: payrollAmendmentAttendanceView(row.month, row.workAdjustmentId, row.workDays, locale),
      })) ?? [],
  };
}
export function payrollAmendmentCorrectionFailure(
  failure: Failure | null,
  index: number,
  kind: "compensation" | "input",
): Failure | null {
  if (!failure || index < 0) return null;
  const prefix = `corrections[${index}].${kind}.`,
    fields = Object.fromEntries(
      Object.entries(failure.fields)
        .filter(([key]) => key.startsWith(prefix))
        .map(([key, value]) => [`terms.${key.slice(prefix.length)}`, value]),
    );
  return Object.keys(fields).length ? { ...failure, fields } : null;
}
