import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { AttendanceAdjustmentReview } from "../../domain/entities/attendance-adjustment-review";
import type { AttendanceAdjustmentSummary } from "../../domain/entities/attendance-adjustment-summary";
import { attendanceAdjustmentMessages } from "../i18n/attendance-adjustment-messages";
import { workPeriodMessages } from "../i18n/work-period-messages";
import { attendanceEntryView } from "./attendance-view";

export function attendanceAdjustmentReviewView(review: AttendanceAdjustmentReview, locale: Locale) {
  const text = attendanceAdjustmentMessages(locale),
    work = workPeriodMessages(locale),
    number = new Intl.NumberFormat(locale);
  const timezone = review.period.timezone ?? "UTC";
  return {
    properties: [
      { label: work.employeeNumber, value: review.employee.employeeNumber },
      { label: text.date, value: review.current.workDate },
      { label: work.timezone, value: timezone },
      { label: text.revision, value: number.format(review.adjustmentRevision) },
    ],
    columns: [
      { id: "source", label: text.revision },
      { id: "fact", label: text.status },
      { id: "schedule", label: text.schedule },
      { id: "minutes", label: text.minutes, numeric: true },
      { id: "overtime", label: text.overtime, numeric: true },
    ],
    rows: [review.original, review.current].map((day, index) => ({
      id: String(index),
      cells: [
        index === 0 ? text.original : text.current,
        work[day.fact],
        day.schedule.kind === "WORK" ? day.schedule.shift.details.name : work[day.schedule.kind],
        number.format(day.acceptedMinutes),
        number.format(day.overtime.reduce((total, entry) => total + entry.approvedMinutes, 0)),
      ],
    })),
    entries: review.attendance.entries.map((entry) => {
      const view = attendanceEntryView(entry, locale, timezone);
      const time = new Intl.DateTimeFormat(locale, {
        timeZone: timezone,
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(entry.capturedAt));
      return {
        ...view,
        title: `${view.kind} · ${time}`,
        decisionLabel: `${text.decision} · ${view.kind} · ${time}`,
      };
    }),
  };
}
export function attendanceAdjustmentSummaryView(
  summary: AttendanceAdjustmentSummary,
  locale: Locale,
  timezone: string,
) {
  const text = attendanceAdjustmentMessages(locale);
  return [
    { label: text.revision, value: String(summary.revision) },
    { label: text.date, value: summary.workDate },
    {
      label: `${text.recorded} (${timezone})`,
      value: new Intl.DateTimeFormat(locale, {
        timeZone: timezone,
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(summary.recordedAt)),
    },
    { label: text.actor, value: summary.actorId },
    { label: text.reason, value: summary.reason },
    { label: text.reference, value: summary.id },
  ];
}
