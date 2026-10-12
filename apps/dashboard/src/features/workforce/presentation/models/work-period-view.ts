import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { WorkPeriodOverview } from "../../domain/entities/work-period-overview";
import type { WorkPeriodSnapshot } from "../../domain/entities/work-period-snapshot";
import { closedAttendanceTotals } from "../../domain/policies/closed-attendance-totals";
import { workPeriodMessages } from "../i18n/work-period-messages";

export function workPeriodOverviewView(overview: WorkPeriodOverview, locale: Locale) {
  const text = workPeriodMessages(locale),
    period = overview.period,
    attempt = overview.attempt;
  const timezone = period?.timezone ?? overview.companyTimezone;
  const date = new Intl.DateTimeFormat(locale, { timeZone: "UTC", month: "long", year: "numeric" });
  const instant = new Intl.DateTimeFormat(locale, {
    timeZone: timezone,
    dateStyle: "medium",
    timeStyle: "short",
  });
  const number = new Intl.NumberFormat(locale);
  const failureCode = period?.failureCode ?? attempt?.failureCode;
  return {
    month: date.format(new Date(`${overview.month}-01T00:00:00Z`)),
    status: text[period?.status ?? "OPEN"],
    color:
      period?.status === "CLOSED"
        ? ("success" as const)
        : period?.status === "REVIEW_REQUIRED" || attempt?.status === "FAILED"
          ? ("warning" as const)
          : ("informative" as const),
    properties: [
      { label: text.timezone, value: timezone },
      {
        label: text.started,
        value: period?.startedAt ? instant.format(new Date(period.startedAt)) : "—",
      },
      {
        label: text.closed,
        value: period?.closedAt ? instant.format(new Date(period.closedAt)) : "—",
      },
    ],
    progress: attempt
      ? {
          label: `${number.format(attempt.completedEmployees)} / ${number.format(attempt.totalEmployees)}`,
          value:
            attempt.totalEmployees === 0
              ? attempt.status === "SUCCEEDED"
                ? 1
                : 0
              : attempt.completedEmployees / attempt.totalEmployees,
          status: text[attempt.status],
        }
      : null,
    failure: failureCode ? { code: failureCode, fields: {}, parameters: {} } : null,
    notEnded: overview.month >= overview.companyDate.slice(0, 7),
  };
}

export function workPeriodSnapshotView(snapshot: WorkPeriodSnapshot, locale: Locale) {
  const text = workPeriodMessages(locale),
    totals = closedAttendanceTotals(snapshot.days);
  const number = new Intl.NumberFormat(locale);
  const date = new Intl.DateTimeFormat(locale, { timeZone: "UTC", dateStyle: "medium" });
  const instant = new Intl.DateTimeFormat(locale, {
    timeZone: snapshot.period.timezone ?? "UTC",
    dateStyle: "medium",
    timeStyle: "short",
  });
  return {
    properties: [
      { label: text.employeeNumber, value: snapshot.employee.employeeNumber },
      {
        label: text.month,
        value: new Intl.DateTimeFormat(locale, {
          timeZone: "UTC",
          month: "long",
          year: "numeric",
        }).format(new Date(`${snapshot.period.month}-01T00:00:00Z`)),
      },
      { label: text.timezone, value: snapshot.period.timezone ?? "—" },
      {
        label: text.closed,
        value: snapshot.period.closedAt ? instant.format(new Date(snapshot.period.closedAt)) : "—",
      },
    ],
    metrics: [
      { label: text.accepted, value: number.format(totals.acceptedMinutes) },
      { label: text.overtime, value: number.format(totals.overtimeMinutes) },
      { label: text.absent, value: number.format(totals.facts.ABSENCE_RECORDED) },
      { label: text.unrecorded, value: number.format(totals.facts.UNRECORDED) },
    ],
    days: snapshot.days.map((day) => ({
      id: day.workDate,
      cells: [
        date.format(new Date(`${day.workDate}T00:00:00Z`)),
        text[day.fact],
        day.schedule.kind === "WORK" ? day.schedule.shift.details.name : text[day.schedule.kind],
        number.format(day.acceptedMinutes),
        number.format(closedAttendanceTotals([day]).overtimeMinutes),
      ],
    })),
  };
}
