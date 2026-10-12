import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { AttendanceCorrection } from "../../domain/entities/attendance-correction";
import type { AttendanceDay } from "../../domain/entities/attendance-day";
import type { AttendanceEntry } from "../../domain/entities/attendance-entry";
import type { EmployeeAttendanceReview } from "../../domain/entities/employee-attendance-review";
import { attendanceMessages } from "../i18n/attendance-messages";

export function attendanceOverviewView(attendance: EmployeeAttendanceReview, locale: Locale) {
  const text = attendanceMessages(locale),
    number = new Intl.NumberFormat(locale);
  const date = new Intl.DateTimeFormat(locale, { timeZone: "UTC", dateStyle: "medium" });
  return attendance.days.map((day) => ({
    id: day.workDate,
    actionLabel: date.format(new Date(`${day.workDate}T00:00:00Z`)),
    cells: [
      date.format(new Date(`${day.workDate}T00:00:00Z`)),
      number.format(day.acceptedMinutes),
      number.format(day.pendingCount),
      day.correction
        ? text.corrected
        : day.incomplete
          ? text.incomplete
          : day.entries.length === 0
            ? text.unrecorded
            : text.complete,
    ],
  }));
}
export function attendanceEntryView(entry: AttendanceEntry, locale: Locale, timezone: string) {
  const text = attendanceMessages(locale);
  const date = new Intl.DateTimeFormat(locale, {
    timeZone: timezone,
    dateStyle: "medium",
    timeStyle: "medium",
  });
  const number = new Intl.NumberFormat(locale, { maximumFractionDigits: 5 });
  return {
    id: entry.id,
    kind: text[entry.kind],
    status: text[entry.status],
    pending: entry.status === "PENDING",
    properties: [
      { label: text.capturedAt, value: date.format(new Date(entry.capturedAt)) },
      { label: text.receivedAt, value: date.format(new Date(entry.receivedAt)) },
      { label: text.timezone, value: timezone },
      { label: text.original, value: text[entry.initialStatus] },
      {
        label: text.quality,
        value: entry.issues.length
          ? entry.issues.map((issue) => text[issue]).join("; ")
          : text.noIssues,
      },
      {
        label: text.shift,
        value:
          entry.schedule.kind === "WORK" ? entry.schedule.shift.details.name : text.UNSCHEDULED,
      },
      {
        label: text.location,
        value: entry.location
          ? `${number.format(entry.location.latitude)}, ${number.format(entry.location.longitude)}`
          : "—",
      },
      {
        label: text.accuracy,
        value: entry.location ? number.format(entry.location.accuracyMeters) : "—",
      },
      {
        label: text.mocked,
        value: entry.location ? (entry.location.mocked ? text.yes : text.no) : "—",
      },
      ...(entry.review
        ? [
            { label: text.decision, value: text[entry.review.decision] },
            { label: text.reviewedAt, value: date.format(new Date(entry.review.reviewedAt)) },
            { label: text.reviewer, value: entry.review.actorId },
            { label: text.reason, value: entry.review.reason },
          ]
        : []),
    ],
  };
}
export function attendanceCorrectionView(
  correction: AttendanceCorrection,
  locale: Locale,
  timezone: string,
) {
  const text = attendanceMessages(locale),
    date = new Intl.DateTimeFormat(locale, {
      timeZone: timezone,
      dateStyle: "medium",
      timeStyle: "short",
    });
  return [
    {
      label: text.clockIn,
      value: correction.clockIn ? date.format(new Date(correction.clockIn)) : "—",
    },
    {
      label: text.clockOut,
      value: correction.clockOut ? date.format(new Date(correction.clockOut)) : "—",
    },
    {
      label: text.breakMinutes,
      value: new Intl.NumberFormat(locale).format(correction.breakMinutes),
    },
    { label: text.reason, value: correction.reason },
    { label: text.recordedBy, value: correction.actorId },
    { label: text.recordedAt, value: date.format(new Date(correction.recordedAt)) },
  ];
}
export function attendanceDayView(day: AttendanceDay, locale: Locale, timezone: string) {
  return {
    entries: day.entries.map((entry) => attendanceEntryView(entry, locale, timezone)),
    correction: day.correction ? attendanceCorrectionView(day.correction, locale, timezone) : null,
    accepted: new Intl.NumberFormat(locale).format(day.acceptedMinutes),
    pending: new Intl.NumberFormat(locale).format(day.pendingCount),
  };
}
