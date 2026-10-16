import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PayrollAmendmentBasis } from "../../domain/entities/payroll-amendment-preparation";
import type { PayrollWorkDay } from "../../domain/entities/payroll-work-day";
import { payrollAmendmentEditorMessages } from "../i18n/payroll-amendment-editor-messages";
import { payrollCalculationMessages } from "../i18n/payroll-calculation-messages";

export function payrollAmendmentAttendanceChoiceView(
  source: PayrollAmendmentBasis,
  selected: string,
  locale: Locale,
  timezone: string,
) {
  const text = payrollAmendmentEditorMessages(locale),
    adjustment = source.availableWorkAdjustment;
  const stale = selected !== "" && selected !== adjustment?.id;
  return {
    stale,
    options: [
      { value: "", label: text.keepAttendance },
      ...(adjustment
        ? [{ value: adjustment.id, label: `${text.useAttendance} ${adjustment.revision}` }]
        : []),
      ...(stale ? [{ value: selected, label: text.previousAttendance }] : []),
    ],
    properties: [
      {
        label: text.publishedAttendance,
        value: source.workDays
          ? (source.workAdjustmentId ?? text.originalClosing)
          : text.attendanceUnavailable,
      },
      ...(adjustment
        ? [
            {
              label: text.availableAttendance,
              value: `${text.attendanceRevision} ${adjustment.revision}`,
            },
            { label: text.attendanceReference, value: adjustment.id },
            { label: text.attendanceDate, value: adjustment.workDate },
            {
              label: `${text.attendanceRecorded} (${timezone})`,
              value: new Intl.DateTimeFormat(locale, {
                dateStyle: "medium",
                timeStyle: "short",
                timeZone: timezone,
              }).format(new Date(adjustment.recordedAt)),
            },
            { label: text.attendanceReason, value: adjustment.reason },
          ]
        : []),
      ...(stale ? [{ label: text.previousAttendance, value: selected }] : []),
    ],
  };
}
export function payrollAmendmentAttendanceView(
  month: string,
  workAdjustmentId: string | null,
  days: readonly PayrollWorkDay[] | null,
  locale: Locale,
) {
  if (days === null) return null;
  const text = payrollAmendmentEditorMessages(locale),
    work = payrollCalculationMessages(locale);
  return {
    title: `${text.attendanceUsed} · ${month}`,
    properties: [
      { label: text.attendanceReference, value: workAdjustmentId ?? text.originalClosing },
    ],
    columns: [
      { id: "date", label: work.date },
      { id: "schedule", label: work.schedule },
      { id: "attendance", label: work.attendance },
      { id: "holiday", label: work.officialHoliday },
      { id: "overtime", label: text.overtimeMinutes, numeric: true },
    ],
    rows: days.map((day) => ({
      id: day.date,
      cells: [
        day.date,
        work.scheduleKinds[day.schedule],
        work.attendanceKinds[day.attendance],
        day.officialHoliday ? work.yes : work.no,
        String(day.overtime.reduce((total, line) => total + line.minutes, 0)),
      ],
    })),
  };
}
